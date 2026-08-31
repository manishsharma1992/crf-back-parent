import { KeyValue, KeyValuePipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { AnalysisSnapshotView } from './analysis-snapshot.model';

/**
 * BR03 — what this analysis concluded, as it was concluded.
 *
 * Every value here is read from the frozen responses, so nothing on this screen
 * moves when the workbook is republished or the counterparty's financials are
 * refreshed. That is the whole point of the snapshot, and the reason the
 * definition version is shown rather than hidden: it is what makes the conclusion
 * replayable.
 */
@Component({
  selector: 'crf-analysis-snapshot',
  standalone: true,
  imports: [KeyValuePipe, DatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './analysis-snapshot.component.html',
  styleUrl: './analysis-snapshot.component.scss',
})
export class AnalysisSnapshotComponent {
  readonly snapshot = input.required<AnalysisSnapshotView>();

  /**
   * Keeps the server's insertion order. KeyValuePipe sorts alphabetically by
   * default, which would scramble the definition order the flags were authored in
   * — the same order the analyst read them in on the form.
   */
  readonly originalOrder = (_a: KeyValue<string, string>, _b: KeyValue<string, string>): number => 0;
}
