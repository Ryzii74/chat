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
- If no results are found, the response text must be exactly `ничего не найдено` and must not be clickable.
- If results are found, each result must be on its own line and clickable for sending to the engine input.
