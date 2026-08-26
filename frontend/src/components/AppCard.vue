<script setup>
defineProps({
  shadow: Boolean,
  clickable: Boolean,
  padded: { type: Boolean, default: true }
})
</script>

<template>
  <div
    class="app-card"
    :class="{
      'app-card--shadow': shadow,
      'app-card--clickable': clickable,
      'app-card--bare': !padded
    }"
  >
    <slot/>
  </div>
</template>

<style scoped>
.app-card {
  background: var(--color-surface);
  border: var(--border-weak);
  border-radius: var(--radius-md);
  padding: var(--space-5);
}
.app-card--shadow { box-shadow: var(--shadow-sm); }
.app-card--bare { padding: 0; }
.app-card--clickable { cursor: pointer; transition: border-color 0.12s, background 0.12s; }
.app-card--clickable:hover {
  border-color: var(--color-accent);
  background: var(--color-accent-bg);
}
</style>
