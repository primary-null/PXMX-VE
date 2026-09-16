# Operations review fixes

Scope: review findings 2 and 8–13, against `ea07148`. No auth/session, console, build, workflow, or version files changed.

## Verification

Executed with Android Studio JBR:

```sh
bash ./gradlew :app:testDebugUnitTest --no-daemon
python scripts/security_check.py --all
git diff --check
```

Final result: **275 unit tests, 0 failures, 0 errors, 0 skipped**. Security checker and whitespace check passed. The added suites contain 21 `OperationsRegressionTest` tests and 3 `SshLifetimeTest` tests. Existing Gradle/AGP deprecation warnings remain.

## Findings and evidence

| Finding | Fix | Focused regression evidence |
| --- | --- | --- |
| 2: wrong SSH/SFTP node | Shared authenticated `/cluster/status` resolution; no entry-host fallback; full IPv6 handling; profile/node binding before asynchronous work | `sshUpgradeUsesSelectedNodeIpv6NotEntryHost` and `backupSftpUsesSelectedNodeAndVerifiedRootPassword` failed on the original targeting, then passed. Additional unresolved-node and profile-switch guards pass without contacting a transport. |
| 8: duplicate SDN keys | Carry node identity, display it, and use node/zone/type keys | `sdnStatusRetainsDistinctNodeIdentity` failed on equal multi-node rows, then passed with distinct node identities and row keys. |
| 9: failed reads shown as empty/current | Essential endpoint failures reach the existing retaining UI; optional metadata fallbacks rethrow authentication/cancellation; SDN/network/firewall retain failed sections; update status becomes unknown and recovers on successful retry | Apt and storage endpoint regressions failed first. `failedReadSectionsNeverBecomeSuccessfulEmptySnapshots` reproduced failures hidden across 18 endpoint paths. Authentication/cancellation, network/SDN retention, firewall partial retention, syslog retry errors, and update unknown/recovery regressions each failed before their fix and now pass. |
| 10: unbounded SSH lifetime | Whole-operation deadlines (upgrade 10 minutes; download 1 hour), finite connect/request timeouts, cancellation closes the socket, command wait precedes bounded output draining, daemon reader cleanup | `SshLifetimeTest`: open-output cancellation and deadline tests failed on unbounded reader joins; SFTP blocked-connect cancellation also failed before its fix. All pass using fake sockets/streams. |
| 11: arbitrary API password reused as root | Only the explicitly active, exactly matching saved password profile with a verified root PAM session qualifies; unsupported identities are unavailable in UI and repository | Eligibility regression rejected non-root, non-PAM, host/port/account/realm/auth-mode mismatches after failing on the original host-only selection. Positive upgrade/SFTP tests verify root and the selected saved fake secret. |
| 12: USB pending/concurrent overwrite | Allocate using `current=0`; require and submit digest; reread/reallocate on bounded checksum conflicts | Pending-config/digest and conflict-reallocation regressions failed before implementation. Missing-digest refusal and persistent-conflict bound additionally pass. |
| 13: premature deletion success | Await an imgdel UPID's terminal status separately from DELETE; reject task failure; bound polling; retain synchronous success | Running-to-failure regression failed before implementation; running-to-success verifies multiple polls and one DELETE. Existing synchronous deletion tests pass. |

## Deliberate behavior and remaining integration checks

- Essential multi-section reads conservatively fail the refresh instead of publishing incomplete successful snapshots. Prior UI data is retained with an error; newly fetched portions of an aborted snapshot are not published. Optional guest metadata and node-status enrichment still preserve useful cached/unknown state, without swallowing authentication or cancellation.
- Direct SSH/SFTP requires the authenticated node address to be reachable on port 22. Unresolved/ambiguous metadata and unsupported credentials refuse safely; no guessed host or alternate account is used. No new credential-management feature was added.
- No live Proxmox endpoint, root password, upgrade, backup, deletion, or SSH/SFTP server was used. Fake transport coverage does not prove device networking or SSHJ interoperability against every server.
- No Compose/device rendering, MediaStore transfer, or Android instrumentation was executed in this worktree. The SDN key contract and screen wiring are tested/compiled, not instrumented rendering. Parent integration is responsible for these checks and the combined worktree build.
- Cancelling the local SSH client does not promise to stop a remote apt process already launched; timeout text directs users to check the node before retrying.
