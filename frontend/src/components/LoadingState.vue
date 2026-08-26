<script setup>
defineProps({
  text: { type: String, default: '加载中...' }
})
</script>

<template>
  <div class="loading-state">
    <span class="loading-state__dot" aria-hidden="true"></span>
    <span>{{ text }}</span>
  </div>
</template>

<style scoped>
.loading-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  padding: var(--space-6);
  font-size: var(--font-sm);
  color: var(--color-text-muted);
}
.loading-state__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--color-text-muted);
}
</style>
