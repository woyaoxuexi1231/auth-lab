<script setup>
defineProps({
  variant: { type: String, default: 'info' },
  text: { type: String, default: '' }
})
</script>

<template>
  <div class="status-bar" :class="`status-bar--${variant}`">
    <slot>{{ text }}</slot>
  </div>
</template>

<style scoped>
.status-bar {
  padding: 8px 12px;
  font-size: var(--font-sm);
  border: 1px solid;
  border-radius: var(--radius);
  line-height: 1.5;
}
.status-bar--success {
  color: var(--color-success);
  background: var(--color-success-bg);
  border-color: color-mix(in srgb, var(--color-success) 30%, transparent);
}
.status-bar--error {
  color: var(--color-error);
  background: var(--color-error-bg);
  border-color: color-mix(in srgb, var(--color-error) 30%, transparent);
}
.status-bar--warning {
  color: var(--color-warning);
  background: var(--color-warning-bg);
  border-color: color-mix(in srgb, var(--color-warning) 30%, transparent);
}
.status-bar--info {
  color: var(--color-info);
  background: var(--color-info-bg);
  border-color: color-mix(in srgb, var(--color-info) 30%, transparent);
}
</style>
