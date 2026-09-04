import { useEffect, useMemo, useRef, useState, type CSSProperties, type ReactNode } from 'react';

export type TemplatePickerOption = {
  value: string;
  icon: ReactNode;
  title: string;
  description?: string;
};

type TemplatePickerProps = {
  id: string;
  label: string;
  placeholder: string;
  searchPlaceholder: string;
  emptyMessage: string;
  clearSearchLabel: string;
  value: string;
  options: TemplatePickerOption[];
  onChange: (value: string) => void;
};

export function TemplatePicker({
  id,
  label,
  placeholder,
  searchPlaceholder,
  emptyMessage,
  clearSearchLabel,
  value,
  options,
  onChange,
}: TemplatePickerProps) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [opensAbove, setOpensAbove] = useState(false);
  const [panelMaxHeight, setPanelMaxHeight] = useState(480);
  const rootRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const searchRef = useRef<HTMLInputElement>(null);
  const selected = useMemo(() => options.find((option) => option.value === value), [options, value]);
  const filteredOptions = useMemo(() => {
    const normalizedQuery = query.trim().toLocaleLowerCase();
    if (!normalizedQuery) return options;
    return options.filter((option) => `${option.title} ${option.description ?? ''}`.toLocaleLowerCase().includes(normalizedQuery));
  }, [options, query]);

  function closePicker(restoreFocus = false) {
    setOpen(false);
    setQuery('');
    if (restoreFocus) window.requestAnimationFrame(() => triggerRef.current?.focus());
  }

  function updatePanelPlacement() {
    if (window.matchMedia('(max-width: 600px)').matches) return;
    const triggerBounds = triggerRef.current?.getBoundingClientRect();
    if (!triggerBounds) return;
    const availableBelow = window.innerHeight - triggerBounds.bottom - 16;
    const availableAbove = triggerBounds.top - 16;
    const shouldOpenAbove = availableBelow < 360 && availableAbove > availableBelow;
    setOpensAbove(shouldOpenAbove);
    setPanelMaxHeight(Math.max(180, Math.min(520, shouldOpenAbove ? availableAbove : availableBelow)));
  }

  function openPicker() {
    updatePanelPlacement();
    setOpen(true);
  }

  useEffect(() => {
    if (!open) return;
    const previousOverflow = document.body.style.overflow;
    const isMobileSheet = window.matchMedia('(max-width: 600px)').matches;
    if (isMobileSheet) document.body.style.overflow = 'hidden';
    window.requestAnimationFrame(() => searchRef.current?.focus());

    const handleOutsidePointer = (event: PointerEvent) => {
      if (event.target instanceof Node && !rootRef.current?.contains(event.target)) closePicker();
    };
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') closePicker(true);
    };

    document.addEventListener('pointerdown', handleOutsidePointer);
    document.addEventListener('keydown', handleKeyDown);
    window.addEventListener('resize', updatePanelPlacement);
    window.addEventListener('scroll', updatePanelPlacement, true);
    return () => {
      document.removeEventListener('pointerdown', handleOutsidePointer);
      document.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('resize', updatePanelPlacement);
      window.removeEventListener('scroll', updatePanelPlacement, true);
      if (isMobileSheet) document.body.style.overflow = previousOverflow;
    };
  }, [open]);

  return (
    <div ref={rootRef} className={`template-picker ${open ? 'is-open' : ''} ${opensAbove ? 'opens-above' : ''}`} style={{ '--template-picker-max-height': `${panelMaxHeight}px` } as CSSProperties}>
      <span className="template-picker-label" id={`${id}-label`}>{label}</span>
      <button
        ref={triggerRef}
        type="button"
        className="template-picker-trigger"
        aria-labelledby={`${id}-label ${id}-selection`}
        aria-haspopup="dialog"
        aria-expanded={open}
        onClick={() => open ? closePicker() : openPicker()}
      >
        {selected ? <>
          <span className="template-picker-trigger-icon" aria-hidden="true">{selected.icon}</span>
          <span className="template-picker-copy" id={`${id}-selection`}>
            <strong>{selected.title}</strong>
            {selected.description && <small>{selected.description}</small>}
          </span>
        </> : <span className="template-picker-placeholder" id={`${id}-selection`}>{placeholder}</span>}
        <span className="template-picker-chevron" aria-hidden="true" />
      </button>

      {open && <>
        <div className="template-picker-backdrop" aria-hidden="true" onPointerDown={() => closePicker(true)} />
        <section className="template-picker-panel" role="dialog" aria-modal="false" aria-labelledby={`${id}-label`}>
          <div className="template-picker-toolbar">
            <div className="template-picker-search">
              <span className="template-picker-search-icon" aria-hidden="true" />
              <input ref={searchRef} type="search" aria-label={searchPlaceholder} value={query} placeholder={searchPlaceholder} onChange={(event) => setQuery(event.target.value)} />
              {query && <button type="button" className="template-picker-clear" aria-label={clearSearchLabel} onClick={() => { setQuery(''); searchRef.current?.focus(); }}>×</button>}
            </div>
            <span className="template-picker-count">{filteredOptions.length} / {options.length}</span>
          </div>

          <div className="template-picker-scroll-region">
            <div className="template-picker-options" role="listbox" aria-labelledby={`${id}-label`}>
              {filteredOptions.map((option) => {
                const isSelected = option.value === value;
                return <button
                  key={option.value}
                  type="button"
                  role="option"
                  aria-selected={isSelected}
                  className={`template-picker-option ${option.description ? 'has-description' : ''} ${isSelected ? 'is-selected' : ''}`}
                  onClick={() => {
                    if (!isSelected) onChange(option.value);
                    closePicker(true);
                  }}
                >
                  <span className="template-picker-option-icon" aria-hidden="true">{option.icon}</span>
                  <span className="template-picker-copy">
                    <strong>{option.title}</strong>
                    {option.description && <small>{option.description}</small>}
                  </span>
                  {isSelected && <span className="template-picker-check" aria-hidden="true">✓</span>}
                </button>;
              })}
            </div>
            {!filteredOptions.length && <p className="template-picker-empty" role="status">{emptyMessage}</p>}
          </div>
        </section>
      </>}
    </div>
  );
}
