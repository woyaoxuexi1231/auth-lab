<script setup>
defineProps({ text: String })
</script>

<template>
  <pre class="code-block"><code><slot>{{ text }}</slot></code></pre>
</template>

<style scoped>
.code-block {
  margin: var(--space-3) 0;
  padding: var(--space-4);
  background: var(--color-code-bg);
  border: var(--border-weak);
  border-radius: var(--radius);
  font-size: var(--font-xs);
  line-height: 1.7;
  color: var(--color-text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
  overflow-x: auto;
}
</style>
