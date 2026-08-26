<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  open: Boolean,
  title: String,
  width: { type: String, default: '480px' }
})
const emit = defineEmits(['close', 'update:open'])

const savedFocus = ref(null)

function close () {
  emit('close')
  emit('update:open', false)
}

function onKeydown (e) {
  if (e.key === 'Escape' && props.open) close()
}

function onOverlay (e) {
  if (e.target === e.currentTarget) close()
}

watch(() => props.open, (open) => {
  if (open) {
    savedFocus.value = document.activeElement
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = ''
    if (savedFocus.value && typeof savedFocus.value.focus === 'function') savedFocus.value.focus()
  }
})

onMounted(() => window.addEventListener('keydown', onKeydown))
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="app-modal-overlay" @mousedown.self="onOverlay">
      <div
        class="app-modal"
        :style="{ maxWidth: width }"
        role="dialog"
        :aria-modal="true"
        :aria-label="title || '对话框'"
      >
        <div class="app-modal__head">
          <h3 class="app-modal__title">{{ title }}</h3>
          <button class="app-modal__close" type="button" aria-label="关闭" @click="close">×</button>
        </div>
        <div class="app-modal__body">
          <slot/>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.app-modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: var(--space-4);
  background: rgba(26, 26, 26, 0.35);
}
.app-modal {
  width: 100%;
  background: var(--color-surface);
  border: var(--border-weak);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-md);
}
.app-modal__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
  border-bottom: var(--border-weak);
  background: var(--color-surface);
}
.app-modal__title {
  font-size: var(--font-md);
  font-weight: var(--weight-semibold);
}
.app-modal__close {
  font-size: var(--font-lg);
  line-height: 1;
  color: var(--color-text-muted);
  background: none;
  border: none;
  cursor: pointer;
}
.app-modal__close:hover { color: var(--color-text); }
.app-modal__body {
  padding: var(--space-5);
  max-height: 70vh;
  overflow-y: auto;
}
</style>
