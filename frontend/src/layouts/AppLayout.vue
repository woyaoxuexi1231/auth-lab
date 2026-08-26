<script setup>
const iconSrc = `${import.meta.env.BASE_URL}favicon.svg`
</script>

<template>
  <div class="app-layout">
    <nav class="topnav">
      <div class="container topnav__inner">
        <router-link to="/auth-lab" class="topnav__brand">
          <img class="topnav__icon" :src="iconSrc" width="22" height="22" alt="" aria-hidden="true"/>
          Auth Lab
        </router-link>
        <div class="topnav__links">
          <router-link to="/auth-lab/session" class="topnav__link">Session</router-link>
          <router-link to="/auth-lab/jwt" class="topnav__link">JWT</router-link>
          <router-link to="/auth-lab/opaque" class="topnav__link">Opaque</router-link>
          <router-link to="/auth-lab/oauth2" class="topnav__link">OAuth2</router-link>
        </div>
      </div>
    </nav>
    <main class="container app-layout__main">
      <slot/>
    </main>
  </div>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}
.topnav {
  position: sticky;
  top: 0;
  z-index: 100;
  background: var(--color-surface);
  border-bottom: var(--border-weak);
}
.topnav__inner {
  display: flex;
  align-items: center;
  gap: var(--space-5);
  flex-wrap: wrap;
  min-height: var(--nav-height);
  padding-top: var(--space-2);
  padding-bottom: var(--space-2);
}
.topnav__brand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: var(--font-md);
  font-weight: var(--weight-semibold);
  color: var(--color-text);
  white-space: nowrap;
}
.topnav__icon {
  flex-shrink: 0;
  border-radius: 5px;
}
.topnav__brand:hover {
  color: var(--color-accent);
  text-decoration: none;
}
.topnav__links {
  display: flex;
  gap: var(--space-1);
  flex-wrap: wrap;
}
.topnav__link {
  padding: 6px 10px;
  font-size: var(--font-sm);
  font-weight: var(--weight-medium);
  color: var(--color-text-secondary);
  border-radius: var(--radius-sm);
}
.topnav__link:hover {
  color: var(--color-accent);
  background: var(--color-accent-bg);
  text-decoration: none;
}
.topnav__link.router-link-active {
  color: var(--color-accent);
  background: var(--color-accent-bg);
}
.app-layout__main {
  flex: 1;
  padding-top: var(--space-6);
  padding-bottom: var(--space-8);
}
</style>
