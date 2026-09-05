# Report follow-up and escalation contract v0.1

## Timing and ownership

- The initial prompt is due seven days after backend-owned `filedAt`, calculated against backend time.
- An unsure response schedules another prompt three days after the backend records that response.
- Client clocks and client-supplied dates never determine eligibility.
- Events live under the private report and are readable only by its owner. Clients cannot create, change or delete them.

## Lifecycle and recurrence

- A resolved answer enters the existing `CLAIMED_FIXED` flow and does not directly verify a fix or award points.
- Rejecting a claimed fix resumes the same unresolved cycle immediately.
- Reopening after `VERIFIED_FIXED` starts a new cycle from the server-recorded reopen event.
- The internal cycle number is not included in government-facing text. A later cycle may state that the issue recurred after a previously verified resolution.

## Escalation integrity

- Escalation drafts are deterministic and bound to the immutable route ID, Civic Pack version and route snapshot hash.
- A stale binding blocks copy and email actions until the draft is regenerated.
- Templates may use the recorded issue, Prabhag, original complaint, server filing date and supplied acknowledgement.
- Templates never invent elapsed duration, prior contact, acknowledgement, response, SLA or resolution history.
- Opening or copying is not treated as sending. The citizen confirms sending explicitly.
- Every follow-up and escalation event awards zero points.

## Language

- English UI defaults to an English draft.
- Marathi and Hindi UI default to a Marathi draft.
- Citizens may switch the draft between English and Marathi without changing the interface language.
- All interface labels, help, status and error copy exist in English, Marathi and Hindi.

## Local E2E and draft continuity

- Follow-up eligibility is evaluated against server time. The local walkthrough uses an injected adjustable clock only under the `local-e2e` profile; production cannot activate that profile.
- The local fixture uses the normal filing and follow-up endpoints. It does not write a client-selected `filedAt` value.
- A filing-action receipt is bound to owner, report, method, route, Civic Pack version, language, content, recipient, and editable sender details.
- Editable sender details may be retained in tab-scoped session storage for the same private report so a matching receipt survives refresh and resume. Starting over or filing clears this temporary data. Complaint text is not added to this contact cache.
- The immutable route-snapshot hash returned by filing must be copied into frontend report state. Escalation remains blocked when route ID, Civic Pack version, or snapshot hash differs from the filed report.
- With no manual escalation-language choice, English interface selects an English draft and Marathi or Hindi interface selects a Marathi government draft. Both the selector and draft content change together.
- A citizen's manual draft-language choice is authoritative and is not changed by later interface-language changes.
- Edited drafts are cached separately by report, recurrence cycle, escalation route, language, and immutable route-snapshot hash. Switching language restores the corresponding edits without overwriting the other language.
- Copying or opening a draft is not a sent confirmation. The citizen must confirm sending separately, and that confirmation awards zero points.
