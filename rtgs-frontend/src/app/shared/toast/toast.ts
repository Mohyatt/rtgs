import { Component, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ToastService } from '../../services/toast.service';

@Component({
  selector: 'app-toast',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (toast().visible) {
      <div class="toast" [class]="'toast--' + toast().type">
        <span class="toast-icon">
          @switch (toast().type) {
            @case ('success') { ✓ }
            @case ('error') { ✕ }
            @case ('warning') { ⚠ }
            @default { ℹ }
          }
        </span>
        <span>{{ toast().message }}</span>
      </div>
    }
  `
})
export class ToastComponent {
  toastService = inject(ToastService);
  toast = this.toastService.toast;
}
