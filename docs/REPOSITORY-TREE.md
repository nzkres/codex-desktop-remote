# 完整仓库文件树

2026-10-12 核对，分支 codex/desktop-1007-recovery，HEAD cfa2d55。共 266 个 Git 跟踪文件。

本快照完整列出当前分支的 Git 跟踪文件，不代表其他工作树未合并的最新修改。不列 .git 内部数据、忽略的依赖/构建输出、真实身份、私密日志和临时维护资料。新增教学文档与本机测试菜单尚未进入该跟踪快照。

```text
codex-desktop-remote/
├── .github/
│   ├── ISSUE_TEMPLATE/
│   │   └── test-feedback.yml
│   └── workflows/
│       ├── android.yml
│       └── core.yml
├── android-native/
│   ├── app/
│   │   ├── src/
│   │   │   ├── androidTest/
│   │   │   │   └── java/
│   │   │   │       └── dev/
│   │   │   │           └── codex/
│   │   │   │               └── remote/
│   │   │   │                   ├── ChatPolishUiTest.kt
│   │   │   │                   ├── ChatReadingControlsUiTest.kt
│   │   │   │                   ├── CompactSpacingUiTest.kt
│   │   │   │                   ├── CompactTypographyTest.kt
│   │   │   │                   ├── ContentCopyUiTest.kt
│   │   │   │                   ├── ConversationTimeUiTest.kt
│   │   │   │                   ├── DraftAndActionsUiTest.kt
│   │   │   │                   ├── GatewaySyncTest.kt
│   │   │   │                   ├── GlassAndNotificationTest.kt
│   │   │   │                   ├── GlassSendTest.kt
│   │   │   │                   ├── LiveTranscriptUiTest.kt
│   │   │   │                   ├── NotificationServiceTest.kt
│   │   │   │                   ├── ProcessingPolishUiTest.kt
│   │   │   │                   ├── TaskRecallUiTest.kt
│   │   │   │                   ├── TaskTargetNetworkTest.kt
│   │   │   │                   ├── TaskTargetSwitchUiTest.kt
│   │   │   │                   ├── TerminalControlUiTest.kt
│   │   │   │                   ├── TerminalNetworkTest.kt
│   │   │   │                   ├── TerminalTranscriptUiTest.kt
│   │   │   │                   ├── TestEvidence.kt
│   │   │   │                   └── TranscriptUiTest.kt
│   │   │   ├── debug/
│   │   │   │   └── AndroidManifest.xml
│   │   │   ├── main/
│   │   │   │   ├── assets/
│   │   │   │   │   └── licenses/
│   │   │   │   │       ├── HAPI-AGPL-3.0.txt
│   │   │   │   │       └── JetBrainsMono-OFL.txt
│   │   │   │   ├── java/
│   │   │   │   │   └── dev/
│   │   │   │   │       └── codex/
│   │   │   │   │           └── remote/
│   │   │   │   │               ├── ActivityFooter.kt
│   │   │   │   │               ├── ChatAppearance.kt
│   │   │   │   │               ├── ChatMessageBubble.kt
│   │   │   │   │               ├── ChatScreen.kt
│   │   │   │   │               ├── ChatTypography.kt
│   │   │   │   │               ├── ComposerEditor.kt
│   │   │   │   │               ├── ContentCopyButton.kt
│   │   │   │   │               ├── ConversationTime.kt
│   │   │   │   │               ├── ConversationWindow.kt
│   │   │   │   │               ├── CopyContent.kt
│   │   │   │   │               ├── DecisionStyle.kt
│   │   │   │   │               ├── DraftStore.kt
│   │   │   │   │               ├── FontLicenseInfo.kt
│   │   │   │   │               ├── Gateway.kt
│   │   │   │   │               ├── GlassActions.kt
│   │   │   │   │               ├── GlassChatHeader.kt
│   │   │   │   │               ├── GlassComposer.kt
│   │   │   │   │               ├── GlassMaterial.kt
│   │   │   │   │               ├── HapiStyle.kt
│   │   │   │   │               ├── HeaderActivity.kt
│   │   │   │   │               ├── MainActivity.kt
│   │   │   │   │               ├── NetworkErrors.kt
│   │   │   │   │               ├── NotificationSettings.kt
│   │   │   │   │               ├── ProcessingLight.kt
│   │   │   │   │               ├── PullHistory.kt
│   │   │   │   │               ├── ReleaseNotes.kt
│   │   │   │   │               ├── ReplyRevealTextView.kt
│   │   │   │   │               ├── RootNavigation.kt
│   │   │   │   │               ├── SendPresentation.kt
│   │   │   │   │               ├── SequentialTranscript.kt
│   │   │   │   │               ├── TaskNotificationService.kt
│   │   │   │   │               ├── TaskNotifications.kt
│   │   │   │   │               ├── TaskRecall.kt
│   │   │   │   │               ├── TaskSignals.kt
│   │   │   │   │               ├── TaskTarget.kt
│   │   │   │   │               ├── TerminalControl.kt
│   │   │   │   │               ├── TerminalScreen.kt
│   │   │   │   │               ├── ToolRunRow.kt
│   │   │   │   │               ├── TranscriptActivity.kt
│   │   │   │   │               ├── TranscriptNavigation.kt
│   │   │   │   │               ├── TranscriptSequence.kt
│   │   │   │   │               ├── TranscriptZebra.kt
│   │   │   │   │               ├── UpdateClient.kt
│   │   │   │   │               ├── UpdateFileProvider.kt
│   │   │   │   │               ├── UpdateGate.kt
│   │   │   │   │               ├── UpdateSecurity.kt
│   │   │   │   │               └── UserMessageText.kt
│   │   │   │   ├── res/
│   │   │   │   │   ├── drawable/
│   │   │   │   │   │   └── ic_notification.xml
│   │   │   │   │   ├── font/
│   │   │   │   │   │   └── jetbrains_mono_regular.ttf
│   │   │   │   │   └── xml/
│   │   │   │   │       └── update_file_paths.xml
│   │   │   │   └── AndroidManifest.xml
│   │   │   └── test/
│   │   │       └── java/
│   │   │           └── dev/
│   │   │               └── codex/
│   │   │                   └── remote/
│   │   │                       ├── ConversationTimeTest.kt
│   │   │                       ├── ConversationWindowTest.kt
│   │   │                       ├── CopyContentTest.kt
│   │   │                       ├── DraftPolicyTest.kt
│   │   │                       ├── ReadingControlsTest.kt
│   │   │                       ├── ReleaseNotesTest.kt
│   │   │                       ├── SendPresentationTest.kt
│   │   │                       ├── TaskRecallTest.kt
│   │   │                       ├── TaskSignalsTest.kt
│   │   │                       ├── TaskTargetTest.kt
│   │   │                       ├── TranscriptActivityTest.kt
│   │   │                       ├── TranscriptPolishTest.kt
│   │   │                       ├── TranscriptSequenceTest.kt
│   │   │                       ├── UpdateAuthTest.kt
│   │   │                       ├── UpdateGateTest.kt
│   │   │                       ├── UpdatePendingTest.kt
│   │   │                       └── UpdateSecurityTest.kt
│   │   └── build.gradle.kts
│   ├── gradle/
│   │   └── wrapper/
│   │       ├── gradle-wrapper.jar
│   │       └── gradle-wrapper.properties
│   ├── FONTS.md
│   ├── README.md
│   ├── build.gradle.kts
│   ├── gradle.properties
│   ├── gradlew
│   ├── gradlew.bat
│   ├── releases.json
│   ├── settings.gradle.kts
│   └── update-trust.default.json
├── deploy/
│   └── test/
│       ├── Caddyfile.review
│       ├── README.md
│       ├── codex-desktop-remote-test-frps.service.review
│       ├── codex-desktop-remote-test.service.review
│       ├── compose.caddy.yaml.review
│       ├── frps.toml.review
│       ├── renew-caddy.sh.review
│       └── test_review.py
├── docs/
│   ├── install/
│   │   ├── ANDROID.md
│   │   ├── README.md
│   │   ├── RELAY.md
│   │   └── WINDOWS.md
│   ├── versions/
│   │   ├── ANDROID.md
│   │   ├── README.md
│   │   ├── RELAY.md
│   │   └── WINDOWS.md
│   ├── ARCHITECTURE.md
│   ├── DESKTOP-26-1007-RECOVERY.md
│   ├── HANDOFF.md
│   ├── ITERATION.md
│   ├── OTA-ACCEPTANCE.md
│   ├── OTA-DEPLOYMENT-REVIEW.md
│   ├── OTA-DEVICE-BASELINE-20261008.md
│   ├── OTA-ISOLATED-DEPLOYMENT-20261008.md
│   ├── OTA-LOCAL-PREPARATION.md
│   ├── OTA-PUBLISH.md
│   ├── OTA-RECOVERY.md
│   ├── OTA.md
│   ├── PROTOTYPES.md
│   ├── TESTING.md
│   ├── UI-BASELINE.md
│   ├── WINDOWS-ALPHA5-MAINTENANCE.md
│   └── WINDOWS-ALPHA7-OTA-REVIEW.md
├── relay/
│   ├── auth/
│   │   └── auth_server.py
│   ├── README.md
│   ├── configure_managed.py
│   ├── control_server.py
│   ├── forwarder.py
│   ├── frp_policy.py
│   ├── gateway_link.py
│   ├── maintenance.py
│   ├── managed.py
│   ├── private_updates.py
│   ├── registry.py
│   ├── release_notes.py
│   ├── releases.json
│   ├── test_configure_managed.py
│   ├── test_forwarder.py
│   ├── test_gateway_link.py
│   ├── test_http.py
│   ├── test_linux_managed.py
│   ├── test_maintenance.py
│   ├── test_private_updates.py
│   ├── test_registry.py
│   ├── test_relay_host.py
│   ├── test_release_notes.py
│   ├── test_update_driver.py
│   ├── transport_settings.py
│   └── update_driver.py
├── scripts/
│   ├── SelfTest.ps1
│   ├── check_android_apk.py
│   ├── check_android_trust.py
│   ├── check_versions.py
│   ├── configure.py
│   ├── diagnose_desktop.mjs
│   ├── probe.mjs
│   ├── relay_ota_smoke.py
│   ├── update_probe.mjs
│   ├── windows_https_ota_smoke.py
│   ├── windows_maintenance_smoke.py
│   └── windows_ota_smoke.py
├── src/
│   ├── vendor/
│   │   ├── conversation-timing.js
│   │   ├── desktop-ipc.js
│   │   ├── desktop-state.js
│   │   └── mobile-sync.js
│   ├── compatibility.js
│   ├── follower.js
│   ├── main.js
│   ├── package.json
│   ├── receipts.js
│   ├── service.js
│   └── version.js
├── test/
│   ├── compatibility.test.mjs
│   ├── desktop_diagnostic.test.mjs
│   ├── follower.test.mjs
│   ├── gui_smoke.py
│   ├── main_smoke.py
│   ├── maintenance_probe.test.mjs
│   ├── reconnect_smoke.py
│   ├── service.test.mjs
│   ├── test_android_apk.py
│   ├── test_android_publisher.py
│   ├── test_android_trust.py
│   ├── test_configure.py
│   ├── test_controller.py
│   ├── test_gui_status.py
│   ├── test_lifecycle.py
│   ├── test_maintenance_smoke.py
│   ├── test_release_catalog.py
│   ├── test_update_authorization.py
│   ├── test_update_bootstrap.py
│   ├── test_update_credentials.py
│   ├── test_update_engine.py
│   ├── test_update_handoff.py
│   ├── test_update_https.py
│   ├── test_update_processes.py
│   ├── test_update_publish.py
│   ├── test_update_security.py
│   ├── test_update_signing.py
│   ├── test_windows_job.py
│   ├── test_windows_maintenance.py
│   └── test_windows_update_driver.py
├── third-party/
│   └── licenses/
│       ├── AGPL-3.0.txt
│       └── HAPI-NOTICE.md
├── updates/
│   ├── PROTOCOL.md
│   ├── __init__.py
│   ├── android_publisher.py
│   ├── authorization.py
│   ├── bootstrap.py
│   ├── child_gate.py
│   ├── cli.py
│   ├── credentials.py
│   ├── engine.py
│   ├── launch.py
│   ├── processes.py
│   ├── publish.py
│   ├── relay_host.py
│   ├── requirements.txt
│   ├── security.py
│   ├── signing.py
│   ├── storage.py
│   └── windows_maintenance.py
├── windows/
│   ├── vendor/
│   │   ├── connection_settings.py
│   │   ├── connector_core.py
│   │   ├── frp_config.py
│   │   ├── runtime_core.py
│   │   ├── transport_worker.py
│   │   └── windows_job.py
│   ├── controller.py
│   ├── demo.py
│   ├── gui.py
│   ├── ota_entry.py
│   ├── release_notes.py
│   ├── releases.json
│   ├── update_driver.py
│   ├── update_helper.py
│   └── update_ui.py
├── .gitattributes
├── .gitignore
├── AGENTS.md
├── CHANGELOG.md
├── PROJECT.md
├── PROVENANCE.md
├── README.md
├── RECOVERY.md
├── STATUS.md
├── WORKLOG.md
├── config.example.json
└── package.json
```

当前本地教学补充：docs/CODE-MAP.md、docs/LEARNING.md、docs/REPOSITORY-TREE.md；本机菜单在 artifacts/learning/Learning.ps1 和 Open-Learning.cmd。
