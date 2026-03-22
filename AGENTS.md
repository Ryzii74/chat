# AGENTS Instructions

## Workflow Rules

- After every code/content change, perform `git add -A`, create a commit, and push to `origin` in the current branch.
- Do not leave local uncommitted changes after finishing a task.
- If push fails, report the error and the exact next command needed.

## Commit Message

- Use concise, descriptive commit messages reflecting the actual change.

## Solver Chat Output Rules

- The `senderName` of each solver response message must match the applied solver method (mode title).
- If multiple methods are applied, each method's output must be sent as separate message(s) with that method title as `senderName`.
- If a single mode produces multiple internal categories/sub-modes (for example, ГаПоИФиКа: `Картины`/`Книги`/`Фильмы`), each category output must be sent as separate message(s) with that category title as `senderName`.
- Pagination is common for all solver modes: load and display results in chunks of 50 via the global `Показать еще 50` button until all results are loaded.
- Do not apply mode-specific output caps that truncate results before pagination (except technical safety limits that are significantly above one page and do not affect normal loading of all matches).
- The `Показать еще 50` control must be only global (not inside message text) and must stay visible while there are pending results.
- If no results are found, the response text must be exactly `ничего не найдено` and must not be clickable.
- If results are found, each result must be on its own line and clickable for sending to the engine input.
