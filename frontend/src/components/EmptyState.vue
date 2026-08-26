<script setup>
defineProps({
  text: { type: String, default: '📭 暂无数据' }
})
</script>

<template>
  <div class="empty-state">
    <slot>{{ text }}</slot>
  </div>
</template>

<style scoped>
.empty-state {
  padding: var(--space-6);
  text-align: center;
  font-size: var(--font-sm);
  color: var(--color-text-muted);
  border: var(--border-weak);
  border-radius: var(--radius-md);
  background: var(--color-surface);
}
</style>
