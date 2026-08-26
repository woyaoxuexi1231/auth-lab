<script setup>
defineProps({
  title: { type: String, required: true },
  subtitle: String
})
</script>

<template>
  <header class="page-header">
    <h1 class="page-header__title">{{ title }}</h1>
    <p v-if="subtitle" class="page-header__subtitle">{{ subtitle }}</p>
  </header>
</template>

<style scoped>
.page-header {
  padding: var(--space-7) 0 var(--space-5);
  border-bottom: var(--border-weak);
  margin-bottom: var(--space-6);
}
.page-header__title {
  font-size: var(--font-2xl);
  font-weight: var(--weight-semibold);
  color: var(--color-text);
}
.page-header__subtitle {
  margin-top: var(--space-2);
  font-size: var(--font-sm);
  color: var(--color-text-muted);
  max-width: 42rem;
  line-height: 1.6;
}
</style>
