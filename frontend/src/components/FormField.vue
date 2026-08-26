<script setup>
defineProps({
  label: String,
  error: String,
  type: { type: String, default: 'text' },
  placeholder: String,
  modelValue: [String, Number],
  autocomplete: String
})
const emit = defineEmits(['update:modelValue', 'enter'])
</script>

<template>
  <div class="form-field">
    <label v-if="label" class="form-field__label">{{ label }}</label>
    <input
      class="form-field__input"
      :class="{ 'form-field__input--error': error }"
      :type="type"
      :placeholder="placeholder"
      :autocomplete="autocomplete"
      :value="modelValue"
      @input="emit('update:modelValue', $event.target.value)"
      @keyup.enter="emit('enter')"
    >
    <p v-if="error" class="form-field__error">{{ error }}</p>
  </div>
</template>

<style scoped>
.form-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}
.form-field__label {
  font-size: var(--font-sm);
  color: var(--color-text-secondary);
  font-weight: var(--weight-medium);
}
.form-field__input {
  width: 100%;
  padding: 8px 12px;
  font-size: var(--font-base);
  color: var(--color-text);
  background: var(--color-surface);
  border: var(--border);
  border-radius: var(--radius);
}
.form-field__input:focus {
  outline: none;
  border-color: var(--color-accent);
  box-shadow: var(--focus-ring);
}
.form-field__input--error { border-color: var(--color-error); }
.form-field__error {
  font-size: var(--font-xs);
  color: var(--color-error);
}
</style>
