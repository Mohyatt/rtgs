import { Injectable, signal } from '@angular/core';

export type ToastType = 'success' | 'error' | 'info' | 'warning';

export interface Toast {
  message: string;
  type: ToastType;
  visible: boolean;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  toast = signal<Toast>({ message: '', type: 'info', visible: false });

  private timer: any;

  show(message: string, type: ToastType = 'info'): void {
    clearTimeout(this.timer);
    this.toast.set({ message, type, visible: true });
    this.timer = setTimeout(() => this.hide(), 3500);
  }

  hide(): void {
    this.toast.update(t => ({ ...t, visible: false }));
  }
}
