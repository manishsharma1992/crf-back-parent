/**
 * LOOKUP members for EcbQuestionsComponent. Merge into the existing class.
 *
 * <p>Searching is neither traversal nor persistence, so it stays here rather than going through the
 * parent — nothing it does changes an answer until the analyst picks something.
 */

  @Input({ required: true }) analysisUid!: string;

  private readonly leverageLendingService = inject(LeverageLendingService);
  private readonly destroyRef = inject(DestroyRef);

  /**
   * A search box per LOOKUP question, SEPARATE from the answer control.
   *
   * <p>They cannot be the same control. While the analyst types "ACM" the box holds partial text,
   * and since every answer re-traverses, the engine would route on a half-typed word. Only a
   * selection writes to the answer.
   */
  private readonly searchControls = new Map<string, FormControl<string | null>>();
  private readonly results = new Map<string, LookupOption[]>();

  /** Keyed by question AND current answer, so the array identity only changes when it must. */
  private readonly itemListCache = new Map<string, IItemList[]>();

  private readonly renderTick = signal(0);

  /**
   * Created on first render of the question and kept, so the subscription is set up once rather
   * than per change-detection pass.
   */
  searchControl(questionKey: string): FormControl<string | null> {
    let control = this.searchControls.get(questionKey);
    if (!control) {
      control = new FormControl<string | null>(null);
      this.searchControls.set(questionKey, control);
      this.watchSearch(questionKey, control);
    }
    return control;
  }

  /**
   * Debounced because the analyst types faster than a trigram search over ten million rows
   * answers; distinct because arrow keys and re-focus re-emit the same text; switchMap because a
   * slower earlier response must not overwrite a faster later one.
   *
   * <p>No minimum-length check here — the backend returns an empty list below three characters, so
   * duplicating the rule in the client would give two places to change it.
   */
  private watchSearch(questionKey: string, control: FormControl<string | null>): void {
    control.valueChanges
      .pipe(
        map(value => (value ?? '').trim()),
        debounceTime(300),
        distinctUntilChanged(),
        switchMap(query => this.leverageLendingService.searchLookupOptions(
          this.analysisUid, LeverageFormType.ECB, questionKey, query, this.locale)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe(options => {
        this.results.set(questionKey, options);
        this.itemListCache.delete(questionKey);
        this.renderTick.update(tick => tick + 1);   // OnPush: results arrive outside any binding
      });
  }

  /**
   * The list as the autocomplete wants it, with a stable array identity.
   *
   * <p>Rebuilt only when the results or the selected answer change. A fresh array per
   * change-detection pass makes a component that tracks by identity re-render, which schedules
   * another pass — the same loop the chip options hit.
   */
  itemListFor(question: QuestionView): IItemList[] {
    this.renderTick();   // re-evaluates when a response lands

    const answer = this.ecbForm.get(question.key)?.value ?? '';
    const cacheKey = `${question.key}|${answer}`;

    let itemList = this.itemListCache.get(cacheKey);
    if (!itemList) {
      itemList = (this.results.get(question.key) ?? []).map(option => ({
        label: option.label,
        value: option.value,
        selected: option.value === answer,
      }));
      this.itemListCache.set(cacheKey, itemList);
    }
    return itemList;
  }

  /**
   * A pick is the only thing that becomes an answer.
   *
   * <p>Emitted upward like any other answer, so the parent re-traverses and persists exactly as it
   * does for a radio — the child stays the component that knows nothing about either.
   */
  onLookupSelected(questionKey: string, value: string): void {
    this.onAnswer(questionKey, value);
  }

  /** What the box shows once a choice is made, and after a reload. */
  lookupDisplay(question: QuestionView): string {
    const answer = this.ecbForm.get(question.key)?.value ?? '';
    const matched = (this.results.get(question.key) ?? [])
      .find(option => option.value === answer);
    return matched?.label ?? answer;
  }
