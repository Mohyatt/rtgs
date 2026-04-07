import { Pipe, PipeTransform } from '@angular/core';

@Pipe({ name: 'sumField', standalone: true })
export class SumFieldPipe implements PipeTransform {
  transform(items: any[], field: string): number {
    if (!items || !field) return 0;
    return items.reduce((acc, item) => acc + (item[field] ?? 0), 0);
  }
}
