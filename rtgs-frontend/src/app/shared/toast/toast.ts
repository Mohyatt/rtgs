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
            @case ('success') { <span class="material-symbols-outlined">check_circle</span> }
            @case ('error') { <span class="material-symbols-outlined">cancel</span> }
            @case ('warning') { <span class="material-symbols-outlined">warning</span> }
            @default { <span class="material-symbols-outlined">info</span> }
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
