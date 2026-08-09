/**
 * The search spinner. Replaces any attempt to set a signal from itemListFor.
 *
 * <p>Writing a signal while the template is being evaluated raises NG0600 — and itemListFor is
 * called during evaluation. The flag is set from the rxjs pipe instead, which runs outside change
 * detection, so nothing is written mid-render.
 *
 * <p>Kept per question rather than as one boolean: two LOOKUPs on a form would otherwise share a
 * spinner and each one's response would stop the other's.
 */

  private readonly searching = signal<ReadonlySet<string>>(new Set());

  isSearching(questionKey: string): boolean {
    return this.searching().has(questionKey);
  }

  private markSearching(questionKey: string, active: boolean): void {
    this.searching.update(current => {
      const next = new Set(current);
      if (active) {
        next.add(questionKey);
      } else {
        next.delete(questionKey);
      }
      return next;
    });
  }

  private watchSearch(questionKey: string, control: FormControl<string | null>): void {
    control.valueChanges
      .pipe(
        map(value => (value ?? '').trim()),
        debounceTime(300),
        distinctUntilChanged(),
        // After the debounce, so the spinner appears when a request is actually about to go out
        // rather than on the first keystroke of a word the analyst is still typing.
        tap(() => this.markSearching(questionKey, true)),
        switchMap(query => this.leverageLendingService
          .searchLookupOptions(this.analysisUid, LeverageFormType.ECB, questionKey, query, this.locale)
          // finalize inside the switchMap: a superseded request must clear its own spinner, and an
          // error must clear it too or it spins forever.
          .pipe(finalize(() => this.markSearching(questionKey, false)))),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(options => {
        this.results.set(questionKey, options);
        this.itemListCache.delete(questionKey);
        this.renderTick.update(tick => tick + 1);
      });
  }
