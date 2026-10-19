<script setup lang="ts">
import {RouterLink, RouterView} from 'vue-router'
</script>

<template>
  <header>

    <div class="wrapper">
      <nav>
        <RouterLink to="/">Home</RouterLink>
        <RouterLink to="/about">About</RouterLink>
      </nav>
    </div>
  </header>

  <RouterView/>
</template>

<style scoped lang="scss">

header {
  .wrapper {
    padding: 3px;
    display: block;
    border-bottom: 2px solid #CCC;

    nav a {
      padding-left: 10px;
      padding-right: 10px;

      text-decoration: none;
    }
  }
}

</style>