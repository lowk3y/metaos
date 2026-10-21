import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { reactive } from 'vue'
import { createChangeFeed, createRefresher, keepUnchanged } from '@/changes'

/** A browser's EventSource, which the test opens, breaks and sends events on */
class FakeEventSource extends EventTarget {
  static opened: FakeEventSource[] = []
  readyState = 0

  constructor(readonly url: string) {
    super()
    FakeEventSource.opened.push(this)
  }

  open() {
    this.readyState = 1
    this.dispatchEvent(new Event('open'))
  }

  send(type: string, data: string) {
    this.dispatchEvent(new MessageEvent(type, { data }))
  }

  // The stream broke: EventSource connects again by itself
  break() {
    this.readyState = 0
    this.dispatchEvent(new Event('error'))
  }

  // An error answer, e.g. a 502: EventSource gives up
  fail() {
    this.readyState = 2
    this.dispatchEvent(new Event('error'))
  }

  close() {
    this.readyState = 2
  }
}

const latest = () => FakeEventSource.opened.at(-1)!

beforeEach(() => {
  FakeEventSource.opened = []
  vi.stubGlobal('EventSource', FakeEventSource)
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

describe('createChangeFeed', () => {
  it('opens one stream, once the first listens', () => {
    const feed = createChangeFeed()
    expect(FakeEventSource.opened).toHaveLength(0)

    feed.listen(() => {})
    feed.listen(() => {})

    expect(FakeEventSource.opened.map((source) => source.url)).toEqual(['/events'])
  })

  it('tells every listener which table changed', () => {
    const feed = createChangeFeed()
    const first = vi.fn()
    const second = vi.fn()
    feed.listen(first)
    feed.listen(second)
    latest().open()

    latest().send('change', 'episodes')

    expect(first).toHaveBeenCalledExactlyOnceWith('episodes')
    expect(second).toHaveBeenCalledExactlyOnceWith('episodes')
  })

  it('stops telling a listener once it stops listening', () => {
    const feed = createChangeFeed()
    const listener = vi.fn()
    const stop = feed.listen(listener)
    latest().open()

    stop()
    latest().send('change', 'episodes')

    expect(listener).not.toHaveBeenCalled()
  })

  it('says * when the stream opens again after it broke, as changes meanwhile went unsaid', () => {
    const feed = createChangeFeed()
    const listener = vi.fn()
    feed.listen(listener)

    latest().open()
    expect(listener).not.toHaveBeenCalled()

    latest().break()
    latest().open()
    expect(listener).toHaveBeenCalledExactlyOnceWith('*')
    expect(FakeEventSource.opened).toHaveLength(1)
  })

  it('says * when MetaOS says it may have missed changes', () => {
    const feed = createChangeFeed()
    const listener = vi.fn()
    feed.listen(listener)
    latest().open()

    latest().send('resync', '*')

    expect(listener).toHaveBeenCalledExactlyOnceWith('*')
  })

  it('connects again when EventSource gives up, less often each time until it opens', () => {
    const feed = createChangeFeed()
    const listener = vi.fn()
    feed.listen(listener)
    latest().open()

    latest().fail()
    vi.advanceTimersByTime(999)
    expect(FakeEventSource.opened).toHaveLength(1)
    vi.advanceTimersByTime(1)
    expect(FakeEventSource.opened).toHaveLength(2)

    latest().fail()
    vi.advanceTimersByTime(1999)
    expect(FakeEventSource.opened).toHaveLength(2)
    vi.advanceTimersByTime(1)
    expect(FakeEventSource.opened).toHaveLength(3)

    latest().open()
    expect(listener).toHaveBeenCalledExactlyOnceWith('*')
    latest().fail()
    vi.advanceTimersByTime(1000)
    expect(FakeEventSource.opened).toHaveLength(4)
  })

  it('waits half a minute at most', () => {
    const feed = createChangeFeed()
    feed.listen(() => {})

    for (let failure = 1; failure <= 10; failure++) {
      latest().fail()
      vi.advanceTimersByTime(30_000)
      expect(FakeEventSource.opened).toHaveLength(failure + 1)
    }
  })
})

describe('createRefresher', () => {
  it('refreshes once for a burst of changes', async () => {
    const refresh = vi.fn()
    const refresher = createRefresher(refresh)

    refresher.changed()
    refresher.changed()
    await vi.advanceTimersByTimeAsync(400)
    refresher.changed()
    await vi.advanceTimersByTimeAsync(100)

    expect(refresh).toHaveBeenCalledOnce()
    await vi.advanceTimersByTimeAsync(5000)
    expect(refresh).toHaveBeenCalledOnce()
  })

  it('refreshes once more after a refresh that changes came during', async () => {
    let done = () => {}
    const refresh = vi.fn(() => new Promise<void>((resolve) => (done = resolve)))
    const refresher = createRefresher(refresh)

    refresher.changed()
    await vi.advanceTimersByTimeAsync(500)
    refresher.changed()
    refresher.changed()
    await vi.advanceTimersByTimeAsync(5000)
    expect(refresh).toHaveBeenCalledOnce()

    done()
    await vi.advanceTimersByTimeAsync(500)
    expect(refresh).toHaveBeenCalledTimes(2)
  })

  it('while the page is hidden, refreshes only once it is shown again', async () => {
    const refresh = vi.fn()
    const refresher = createRefresher(refresh)

    refresher.hide()
    refresher.changed()
    refresher.changed()
    await vi.advanceTimersByTimeAsync(5000)
    expect(refresh).not.toHaveBeenCalled()

    refresher.show()
    await vi.advanceTimersByTimeAsync(500)
    expect(refresh).toHaveBeenCalledOnce()
  })

  it('refreshes a page shown after a minute hidden, for what MetaOS is not told about', async () => {
    const refresh = vi.fn()
    const refresher = createRefresher(refresh)

    refresher.hide()
    await vi.advanceTimersByTimeAsync(10_000)
    refresher.show()
    await vi.advanceTimersByTimeAsync(500)
    expect(refresh).not.toHaveBeenCalled()

    refresher.hide()
    await vi.advanceTimersByTimeAsync(60_000)
    refresher.show()
    await vi.advanceTimersByTimeAsync(500)
    expect(refresh).toHaveBeenCalledOnce()
  })

  it('keeps refreshing after a refresh failed', async () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})
    const refresh = vi.fn().mockRejectedValueOnce(new Error('MetaOS answered 502'))
    const refresher = createRefresher(refresh)

    refresher.changed()
    await vi.advanceTimersByTimeAsync(500)
    expect(error).toHaveBeenCalledOnce()

    refresher.changed()
    await vi.advanceTimersByTimeAsync(500)
    expect(refresh).toHaveBeenCalledTimes(2)
  })

  it('stops once disposed', async () => {
    const refresh = vi.fn()
    const refresher = createRefresher(refresh)

    refresher.changed()
    refresher.dispose()
    refresher.changed()
    await vi.advanceTimersByTimeAsync(5000)

    expect(refresh).not.toHaveBeenCalled()
  })
})

describe('keepUnchanged', () => {
  const episode = () => ({
    id: 'e1',
    title: 'Kotlin',
    people: [{ personId: 'p1', name: 'Ana', roles: ['HOST'] }],
    koofrFolders: [{ path: '/S05E08', photos: [{ path: '/S05E08/1.jpg' }, { path: '/S05E08/2.jpg' }] }],
  })

  it('keeps what was there when nothing changed', () => {
    const previous = episode()

    expect(keepUnchanged(previous, episode())).toBe(previous)
  })

  it('takes what changed and keeps the rest as it was', () => {
    const previous = episode()
    const next = { ...episode(), title: 'Kotlin 2' }

    const kept = keepUnchanged(previous, next)

    expect(kept).toEqual(next)
    expect(kept).not.toBe(previous)
    expect(kept.people).toBe(previous.people)
    expect(kept.koofrFolders).toBe(previous.koofrFolders)
  })

  it('matches items in lists by ID, so a new one at the top changes none after it', () => {
    const previous = [{ id: 'e1', title: 'One' }, { id: 'e2', title: 'Two' }]
    const next = [{ id: 'e3', title: 'Three' }, { id: 'e1', title: 'One' }, { id: 'e2', title: 'Two' }]

    const kept = keepUnchanged(previous, next)

    expect(kept).toEqual(next)
    expect(kept[1]).toBe(previous[0])
    expect(kept[2]).toBe(previous[1])
  })

  it('matches items without an ID by where they are', () => {
    const previous = episode().koofrFolders[0]!.photos
    const next = [{ path: '/S05E08/1.jpg' }, { path: '/S05E08/3.jpg' }]

    const kept = keepUnchanged(previous, next)

    expect(kept).toEqual(next)
    expect(kept[0]).toBe(previous[0])
  })

  it('takes a field that went or came', () => {
    expect(keepUnchanged({ a: 1, b: 2 }, { a: 1 })).toEqual({ a: 1 })
    expect(keepUnchanged({ a: 1 }, { a: 1, b: null })).toEqual({ a: 1, b: null })
    expect(keepUnchanged({ a: 1 }, { b: 1 })).toEqual({ b: 1 })
    expect(keepUnchanged(episode(), null)).toBeNull()
    expect(keepUnchanged(null, { a: 1 })).toEqual({ a: 1 })
    expect(keepUnchanged(undefined, [1])).toEqual([1])
  })

  it("keeps Vue's reactive data as it was, so nothing that shows it updates", () => {
    const shown = reactive(episode())

    expect(keepUnchanged(shown, episode())).toBe(shown)
    const kept = keepUnchanged(shown, { ...episode(), title: 'Kotlin 2' })
    expect(kept.koofrFolders).toBe(shown.koofrFolders)
    expect(kept.koofrFolders[0]!.photos).toBe(shown.koofrFolders[0]!.photos)
  })
})
