import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'filterRole', standalone: true })
export class FilterRolePipe implements PipeTransform {
  transform(items: any[], role: string): any[] {
    if (!items || !role) return items ?? [];
    return items.filter(item => item.role === role);
  }
}
