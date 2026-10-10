# V42 companion backend patch — individual admin delete + enforced lockdown

This archive is based on the supplied V41 source. It adds the following backward-compatible behaviors.

## Individual admin message deletion

New endpoint: `DELETE /api/admin/messages/:id`

- Uses existing `requireAdminAuth` middleware.
- Validates ID and loads the persisted message.
- Tombstones the selected message with `deleted: true` and a placeholder text; removes the active `file_url` from persisted message data.
- Logs the admin action and broadcasts `chat:message_deleted`.
- Leaves `POST /api/admin/messages/clear` as the distinct whole-history action.
- Does not delete the physical storage object; this avoids deleting a potentially shared/unrelated file. It removes the active URL from the message record. Orphan-object cleanup requires a separate ownership-checked process.

## Emergency lockdown

Adds `emergencyLockdown` / `lockdownMessage`, rejects user login and authenticated user requests while active, invalidates persisted user sessions on activation, and broadcasts the lockdown event so the realtime Durable Object closes user sockets. Admin endpoints remain available to disable lockdown.

## Files modified

- `server.cloudflare.ts`
- `server.ts`
- `worker/realtime.ts`

## Before deployment

This patch was syntax-transpiled for TypeScript, but not type-checked with the full project dependency graph, deployed, or tested against live Supabase. Review it and test on staging first. No SQL migration is included because these flags use the existing settings JSON and the deletion route uses the existing message schema's `deleted` field; confirm that your live Supabase schema matches this V41 source.

Validation checklist: admin can delete one selected message; a user token cannot; the tombstone persists after refresh; realtime clients receive the deletion event; clear-all remains separate; lockdown rejects user login and requests, closes user sockets, and remains reversible by admin; normal login/uploads/messages/timer/calls continue to work.
