<script setup>
// 全局 toast 容器：渲染 useToast 单例列表，多条堆叠、自动/点击关闭。
import { useToast } from '../composables/useToast'

const { toasts, remove } = useToast()
</script>

<template>
  <div class="toast-container" aria-live="polite">
    <TransitionGroup name="toast">
      <div
        v-for="t in toasts"
        :key="t.id"
        class="toast"
        :class="`toast--${t.type}`"
        title="点击关闭"
        @click="remove(t.id)"
      >
        {{ t.message }}
      </div>
    </TransitionGroup>
  </div>
</template>

<style scoped>
.toast-container {
  position: fixed;
  top: 72px; /* 避开 sticky 顶栏 */
  right: var(--space-5);
  z-index: 1000;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--space-2);
  max-width: min(360px, calc(100vw - var(--space-8)));
  pointer-events: none;
}
.toast {
  pointer-events: auto;
  padding: var(--space-3) var(--space-4);
  border: 1px solid;
  border-radius: var(--radius);
  box-shadow: var(--shadow-sm);
  font-size: var(--font-sm);
  line-height: 1.5;
  max-width: 100%;
  cursor: pointer;
  background: var(--color-surface);
}
.toast--info { color: var(--color-info); background: var(--color-info-bg); border-color: var(--color-info); }
.toast--success { color: var(--color-success); background: var(--color-success-bg); border-color: var(--color-success); }
.toast--warning { color: var(--color-warning); background: var(--color-warning-bg); border-color: var(--color-warning); }
.toast--error { color: var(--color-error); background: var(--color-error-bg); border-color: var(--color-error); }

.toast-enter-active,
.toast-leave-active {
  transition: all 0.25s ease;
}
.toast-enter-from {
  opacity: 0;
  transform: translateX(24px);
}
.toast-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
.toast-move {
  transition: transform 0.25s ease;
}
</style>
