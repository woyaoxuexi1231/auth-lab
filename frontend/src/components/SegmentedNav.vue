<script setup>
defineProps({
  items: { type: Array, required: true },
  modelValue: { type: [String, Number], required: true }
})
const emit = defineEmits(['update:modelValue', 'change'])

function select (value) {
  emit('update:modelValue', value)
  emit('change', value)
}
</script>

<template>
  <nav class="segmented">
    <button
      v-for="it in items"
      :key="it.value"
      type="button"
      class="segmented__item"
      :class="{ 'segmented__item--active': modelValue === it.value }"
      @click="select(it.value)"
    >{{ it.label }}</button>
  </nav>
</template>

<style scoped>
.segmented {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 2px;
  padding: 3px;
  border-radius: var(--radius);
  background: var(--color-surface-muted);
  border: var(--border-weak);
}
.segmented__item {
  padding: 6px 12px;
  font-size: var(--font-sm);
  font-weight: var(--weight-medium);
  color: var(--color-text-secondary);
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  cursor: pointer;
  white-space: nowrap;
}
.segmented__item:hover { color: var(--color-text); }
.segmented__item--active {
  color: var(--color-accent);
  background: var(--color-surface);
  font-weight: var(--weight-semibold);
  box-shadow: var(--shadow-sm);
}
</style>
