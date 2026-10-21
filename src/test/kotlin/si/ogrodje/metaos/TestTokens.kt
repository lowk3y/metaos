package si.ogrodje.metaos

/** realm_access of an approved user's token (Security.kt), for test JWTs: `jwt().jwt { it.claim("realm_access", APPROVED) }` */
val APPROVED: Map<String, Any> = mapOf("roles" to listOf("default-roles-metaos", APPROVED_ROLE))
