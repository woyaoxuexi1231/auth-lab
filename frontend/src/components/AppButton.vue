<script setup>
import { computed } from 'vue'

const props = defineProps({
  variant: { type: String, default: 'primary' },
  size: { type: String, default: 'md' },
  disabled: Boolean,
  loading: Boolean,
  type: { type: String, default: 'button' },
  block: Boolean
})
const emit = defineEmits(['click'])

const cls = computed(() => [
  'app-btn',
  `app-btn--${props.variant}`,
  `app-btn--${props.size}`,
  { 'app-btn--block': props.block }
])

function onClick (e) {
  if (props.disabled || props.loading) return
  emit('click', e)
}
</script>

<template>
  <button :class="cls" :type="type" :disabled="disabled || loading" @click="onClick">
    <slot/>
  </button>
</template>

<style scoped>
.app-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-1);
  font-family: inherit;
  border-radius: var(--radius);
  cursor: pointer;
  transition: background 0.12s, border-color 0.12s, opacity 0.12s;
  white-space: nowrap;
  border: 1px solid transparent;
}
.app-btn:disabled { opacity: 0.5; cursor: default; }

.app-btn--sm { padding: 4px 10px; font-size: var(--font-xs); }
.app-btn--md { padding: 7px 14px; font-size: var(--font-sm); }
.app-btn--lg { padding: 10px 18px; font-size: var(--font-base); }
.app-btn--block { width: 100%; }

.app-btn--primary {
  background: var(--color-accent);
  color: var(--color-accent-ink);
  border-color: var(--color-accent);
  font-weight: var(--weight-medium);
}
.app-btn--primary:hover:not(:disabled) {
  background: var(--color-accent-hover);
  border-color: var(--color-accent-hover);
}

.app-btn--secondary {
  background: var(--color-surface);
  color: var(--color-text);
  border-color: var(--color-border);
}
.app-btn--secondary:hover:not(:disabled) {
  background: var(--color-surface-muted);
}

.app-btn--ghost {
  background: transparent;
  color: var(--color-text-secondary);
}
.app-btn--ghost:hover:not(:disabled) {
  color: var(--color-accent);
  background: var(--color-accent-bg);
}

.app-btn--danger {
  background: var(--color-surface);
  color: var(--color-error);
  border-color: var(--color-error);
}
.app-btn--danger:hover:not(:disabled) { background: var(--color-error-bg); }

.app-btn--warning {
  background: var(--color-warning-bg);
  color: var(--color-warning);
  border-color: var(--color-warning);
}
.app-btn--warning:hover:not(:disabled) { background: var(--color-surface); }
</style>
