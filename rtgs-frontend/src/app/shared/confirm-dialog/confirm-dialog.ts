import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormControl } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatInputModule } from '@angular/material/input';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';

export interface ChargeMissionOption {
  id: number;
  nomComplet: string;
  email?: string;
  interventionsActives?: number;
  interventionsACloturer?: number;
  totalEnCharge?: number;
  pourcentageCharge?: number;
}

export interface ConfirmDialogData {
  title: string;
  message: string;
  requireComment?: boolean;
  confirmLabel?: string;
  cancelLabel?: string;
  danger?: boolean;
  chargeMissionOptions?: ChargeMissionOption[];
  preselectedChargeMissionId?: number;
  showChargeInfo?: boolean;
}

@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, MatDialogModule, MatButtonModule, MatInputModule, MatFormFieldModule, MatSelectModule],
  styles: [`
    .cdm-option:hover { background: var(--navy3); }
    .cdm-option--selected { background: #EEF2FF !important; border-left: 3px solid var(--blue); }
    .cdm-option:last-child { border-bottom: none; }
  `],
  template: `
    <div class="modal-overlay" (click)="cancel()">
      <div class="modal" (click)="$event.stopPropagation()">
        <div class="modal-header">
          <span class="modal-title">{{ data.title }}</span>
          <button class="modal-close" (click)="cancel()">×</button>
        </div>
        <div class="modal-body">
          <p style="color:var(--text-secondary); margin-bottom: 1rem;">{{ data.message }}</p>
          @if (data.requireComment) {
            <div class="warning-box" style="margin-bottom:1rem;">
              <strong>Commentaire requis</strong><br>
              <span style="font-size:0.85rem;">Veuillez justifier cette action.</span>
            </div>
            <mat-form-field appearance="outline" class="full-width">
              <mat-label>Commentaire</mat-label>
              <textarea matInput [formControl]="commentControl" rows="3" placeholder="Saisissez un commentaire..."></textarea>
            </mat-form-field>
          }
          @if (data.chargeMissionOptions?.length) {
            <div style="margin-top:0.5rem;">
              <label style="display:block; font-size:.85rem; font-weight:500; margin-bottom:.5rem;">Chargé de mission</label>
              @if (data.showChargeInfo) {
                <div style="max-height:200px; overflow-y:auto; border:1px solid var(--border); border-radius:8px;">
                  @for (u of data.chargeMissionOptions; track u.id) {
                    <div class="cdm-option" [class.cdm-option--selected]="chargeMissionControl.value === u.id"
                         (click)="chargeMissionControl.setValue(u.id)"
                         style="padding:.75rem 1rem; cursor:pointer; border-bottom:1px solid var(--border); display:flex; justify-content:space-between; align-items:center;">
                      <div>
                        <div style="font-weight:500;">{{ u.nomComplet }}</div>
                        <div style="font-size:.75rem; color:var(--text-muted);">{{ u.email }}</div>
                      </div>
                      <div style="text-align:right;">
                        <div style="font-size:.85rem;">
                          <span style="font-weight:600;" [style.color]="(u.totalEnCharge ?? 0) >= 5 ? 'var(--red)' : (u.totalEnCharge ?? 0) >= 3 ? 'var(--orange)' : 'var(--green)'">
                            {{ u.totalEnCharge ?? 0 }}
                          </span>
                          <span style="color:var(--text-muted);"> interventions</span>
                        </div>
                        <div style="font-size:.7rem; color:var(--text-muted);">{{ u.pourcentageCharge ?? 0 }}% de la charge</div>
                      </div>
                    </div>
                  }
                </div>
              } @else {
                <mat-form-field appearance="outline" class="full-width">
                  <mat-label>Chargé de mission</mat-label>
                  <mat-select [formControl]="chargeMissionControl" placeholder="Sélectionner">
                    @for (u of data.chargeMissionOptions; track u.id) {
                      <mat-option [value]="u.id">{{ u.nomComplet }}</mat-option>
                    }
                  </mat-select>
                </mat-form-field>
              }
            </div>
          }
        </div>
        <div class="modal-footer">
          <button class="btn btn-secondary" (click)="cancel()">
            {{ data.cancelLabel || 'Annuler' }}
          </button>
          <button class="btn" [class.btn-danger]="data.danger" [class.btn-primary]="!data.danger"
                  [disabled]="(data.requireComment && !commentControl.value?.trim()) || (!!(data.chargeMissionOptions?.length) && chargeMissionControl.value == null)"
                  (click)="confirm()">
            {{ data.confirmLabel || 'Confirmer' }}
          </button>
        </div>
      </div>
    </div>
  `
})
export class ConfirmDialogComponent {
  commentControl = new FormControl('');
  chargeMissionControl = new FormControl<number | null>(null);

  constructor(
    public dialogRef: MatDialogRef<ConfirmDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: ConfirmDialogData
  ) {
    if (data.preselectedChargeMissionId) {
      this.chargeMissionControl.setValue(data.preselectedChargeMissionId);
    }
  }

  confirm() {
    if (this.data.requireComment && !this.commentControl.value?.trim()) return;
    const opts = this.data.chargeMissionOptions?.length ?? 0;
    if (opts > 0 && this.chargeMissionControl.value == null) return;
    this.dialogRef.close({
      confirmed: true,
      comment: this.commentControl.value,
      chargeMissionId: opts > 0 ? this.chargeMissionControl.value! : undefined
    });
  }

  cancel() {
    this.dialogRef.close({ confirmed: false });
  }
}
