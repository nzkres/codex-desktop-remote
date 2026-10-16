"""Static, credential-free error guide for this Windows connector.

Only source literals and reviewed synthetic codes are listed. Runtime data is never read.
"""

from __future__ import annotations

import ast
from pathlib import Path
import re

_DECLARED_CODES = frozenset({
    'permissions_unverified',
    'rollback_context_unverified',
    'recovery_containment_unverified',
    'recovery_desktop_unverified',
    'recovery_failed',
    'recovery_identity_unverified',
    'recovery_interrupted',
    'recovery_inventory_incomplete',
    'recovery_not_required',
    'recovery_owned_process_alive',
    'recovery_port_in_use',
    'recovery_preservation_changed',
    'recovery_state_changed',
    'recovery_task_unverified',
    'windows_recovery_required',

    'activation_outcome_requires_review',
    'active_or_unverified_desktop_task',
    'already_enrolled_or_pending',
    'ambiguous_runtime',
    'android_signer_already_exists',
    'android_signer_create_failed',
    'apk_publisher_verification_required',
    'archive_limits_exceeded',
    'bootstrap_launcher_missing',
    'candidate_activation_unverified',
    'candidate_control_closed',
    'candidate_health_timeout',
    'candidate_health_unverified',
    'candidate_lifetime_transfer_failed',
    'candidate_start_failed',
    'candidate_tree_exit_unconfirmed',
    'cgroup_tree_kill_required',
    'connected_identity_required',
    'connector_already_running',
    'credential_decryption_failed',
    'credential_encryption_failed',
    'delegated_cgroup_required',
    'download_integrity_failed',
    'download_size_mismatch',
    'duplicate_update_state_key',
    'existing_receipts_required',
    'expired_release',
    'external_private_key_required',
    'external_signing_directory_required',
    'forbidden_package_content',
    'gui_command_unverified',
    'gui_normal_exit_unverified',
    'host_home_permissions_invalid',
    'host_path_link_rejected',
    'host_peer_not_owner',
    'host_socket_already_exists',
    'host_socket_path_too_long',
    'host_socket_permissions_invalid',
    'https_required',
    'identity_changed',
    'identity_or_runtime_requires_review',
    'incompatible_release',
    'installed_version_changed',
    'installed_version_missing',
    'insufficient_disk_space',
    'interrupted_update_requires_review',
    'invalid_android_artifact',
    'invalid_android_signer',
    'invalid_candidate_response',
    'invalid_configuration',
    'invalid_credential_file',
    'invalid_device_name',
    'invalid_gui_identity',
    'invalid_host_command',
    'invalid_installation',
    'invalid_json',
    'invalid_lock_name',
    'invalid_package_manifest',
    'invalid_package_tree',
    'invalid_pairing_code',
    'invalid_pairing_response',
    'invalid_publish_metadata',
    'invalid_publish_path',
    'invalid_publisher_key',
    'invalid_relay_response',
    'invalid_relay_url',
    'invalid_release_archive',
    'invalid_release_catalog',
    'invalid_release_envelope',
    'invalid_release_metadata',
    'invalid_release_response',
    'invalid_release_signature',
    'invalid_signing_key_id',
    'invalid_signing_origin',
    'invalid_signing_vault',
    'invalid_terminal_state',
    'invalid_tls_context',
    'invalid_update_ledger',
    'invalid_version_pointer',
    'legacy_alias_probe_requires_review',
    'legacy_identity_rejected',
    'linux_relay_host_required',
    'linux_update_login_required',
    'maintenance_baseline_ledger_changed',
    'maintenance_candidate_changed',
    'maintenance_candidate_version_mismatch',
    'maintenance_fence_changed',
    'maintenance_home_changed',
    'maintenance_interruption_requires_review',
    'maintenance_old_version_mismatch',
    'maintenance_package_changed',
    'maintenance_review_changed',
    'maintenance_review_expired',
    'maintenance_staging_changed',
    'maintenance_state_changed',
    'maintenance_test_channel_required',
    'maintenance_transaction_already_started',
    'maintenance_wait_timeout_required',
    'managed_data_directory_required',
    'managed_relay_binding_unverified',
    'managed_relay_commit_unverified',
    'managed_relay_owner_lost',
    'managed_relay_ready_invalid',
    'managed_relay_slot_invalid',
    'managed_runtime_requires_run',
    'new_artifact_path_required',
    'new_download_path_required',
    'new_external_review_required',
    'new_installation_home_required',
    'new_signing_directory_required',
    'new_stage_required',
    'no_update_available',
    'not_paired',
    'old_process_exit_unconfirmed',
    'old_process_owner_changed',
    'old_process_owner_unverified',
    'one_publisher_key_source_required',
    'original_gui_absence_unverified',
    'package_file_changed',
    'package_file_set_changed',
    'package_source_unavailable',
    'paired_installation_required',
    'preservation_changed',
    'previous_runtime_requires_review',
    'private_directory_failed',
    'private_feed_path_required',
    'relay_host_response_invalid',
    'relay_host_unavailable',
    'relay_response_too_large',
    'release_replay_rejected',
    'reviewed_gui_not_running',
    'reviewer_alias_recovery_not_verified',
    'runtime_binary_changed',
    'runtime_cleanup_requires_review',
    'same_sequence_replacement_rejected',
    'same_version_replacement_rejected',
    'signing_provision_preflight_failed',
    'signing_provision_record_exists',
    'signing_provision_requires_review',
    'signing_vault_create_failed',
    'staged_package_changed',
    'staged_package_missing',
    'state_directory_not_owned',
    'state_path_link_rejected',
    'task_channel_unavailable',
    'terminal_identity_changed',
    'unfinished_update_requires_review',
    'unsafe_archive_entry',
    'unsafe_archive_path',
    'unsafe_local_path',
    'unsafe_package_tree',
    'unsupported_package_component',
    'unsupported_update_component',
    'unsupported_update_platform',
    'untrusted_release_key',
    'untrusted_update_url',
    'update_access_denied',
    'update_authentication_required',
    'update_command_too_large',
    'update_device_identity_unavailable',
    'update_device_not_paired',
    'update_download_failed',
    'update_fetch_failed',
    'update_handoff_timeout',
    'update_handoff_unverified',
    'update_login_changed',
    'update_login_exists_logout_first',
    'update_login_failed',
    'update_login_input_invalid',
    'update_login_permissions_invalid',
    'update_login_record_invalid',
    'update_login_response_invalid',
    'update_or_application_already_running',
    'update_path_link_rejected',
    'update_pointer_changed',
    'update_redirect_rejected',
    'update_state_too_large',
    'update_trust_not_provisioned',
    'update_unconfigured',
    'use_existing_device_pairing',
    'use_windows_install_button',
    'windows_identity_unavailable',
    'windows_lifetime_transfer_required',
    'windows_owner_required',
    'windows_required',
    'windows_signing_required',
    'wrong_installation_platform',
    'wrong_installed_component',
    'wrong_release_target',
})

# These codes come from the GUI's own safe projection or the bounded network
# classifier. They remain documented even when a particular release has not
# emitted one yet. Do not infer them from a status such as "unverified".
_EXTRA_CODES = frozenset({
    'operation_failed', 'update_operation_failed', 'update_record_unreadable',
    'update_needs_review', 'update_rolled_back', 'gateway_failed', 'transport_failed', 'input_required',
    'connection_unavailable',
    'relay_tls_certificate_expired', 'relay_tls_untrusted', 'relay_timeout',
    'relay_tls_handshake_failed',
    'relay_dns_failed', 'relay_connection_refused', 'relay_network_unavailable',
    'relay_http_404', 'relay_http_401', 'relay_http_403', 'relay_http_429',
    'relay_http_5xx', 'relay_http_unexpected',
})

_SOURCE_FILES = (
    'windows/runtime_recovery.py',
    'windows/vendor/connector_core.py', 'windows/controller.py', 'windows/update_driver.py',
    'windows/update_ui.py', 'windows/gui.py', 'windows/vendor/frp_config.py',
    'windows/vendor/runtime_core.py', 'windows/vendor/windows_job.py',
    'windows/vendor/transport_worker.py',
    'updates/authorization.py', 'updates/credentials.py', 'updates/engine.py',
    'updates/processes.py', 'updates/security.py', 'updates/storage.py',
    'updates/windows_maintenance.py', 'updates/bootstrap.py', 'updates/cli.py',
    'updates/relay_host.py', 'updates/signing.py', 'updates/android_publisher.py',
    'updates/publish.py',
)
_CODE_SHAPE = re.compile(r'[a-z][a-z0-9_]{0,79}\Z')

_COMMON = {
    'recovery_failed': ('恢复', '恢复检查未能取得完整可用的核验结果。', '保留现场与核验记录，处理具体原因后重新核验；不清空身份或回执。'),
    'windows_recovery_required': ('恢复', '恢复流程需要原 Windows 账户与本机核验。', '保留现场与核验记录，处理具体原因后重新核验；不清空身份或回执。'),
    'recovery_state_changed': ('恢复', '恢复期间身份、配置、回执或安装版本发生了变化。', '保留现场与核验记录，处理具体原因后重新核验；不清空身份或回执。'),
    'recovery_preservation_changed': ('恢复', '恢复期间任务数据或配置发生了变化。', '保留现场与核验记录，处理具体原因后重新核验；不清空身份或回执。'),
    'recovery_port_in_use': ('恢复', '本机任务端口仍有监听者。', '保留现场与核验记录，处理具体原因后重新核验；不清空身份或回执。'),
    'recovery_not_required': ('恢复', '当前没有异常运行保护，不需要执行冷恢复。', '保留现场与核验记录，处理具体原因后重新核验；不清空身份或回执。'),
    'recovery_identity_unverified': ('恢复', '现有设备身份或运行绑定无法核对。', '保留现场与核验记录，处理具体原因后重新核验；不清空身份或回执。'),
    'recovery_desktop_unverified': ('恢复', '当前 Desktop 安装位置无法唯一核对。', '保留现场与核验记录，处理具体原因后重新核验；不清空身份或回执。'),
    'recovery_containment_unverified': ('恢复', '旧运行周期缺少已验证的进程树容器证据。', '保留现场与核验记录，处理具体原因后重新核验；不清空身份或回执。'),
    'recovery_owned_process_alive': ('恢复', '发现本连接器的旧运行进程仍在。', '保留现场，先让原实例正常断开；不会按进程名强杀。'),
    'recovery_inventory_incomplete': ('恢复', '无法完整核验相关进程。', '使用原 Windows 账户检查进程访问权限后重试核验。'),
    'recovery_interrupted': ('恢复', '恢复事务曾中断，尚未确认保全结果。', '保留 runtime-recovery.json 与身份、回执，由维护者核对事务。'),
    'recovery_task_unverified': ('恢复', '原任务空闲、权限或回执尚未完整核验。', '查看分项诊断，处理原任务后重新核验；不要清除保护。'),
    'question_changed': ('手机答题', '题目版本或答案与原提交不一致。', '刷新核对原题；不要重复提交或更换编号重发。'),
    'question_unsupported': ('手机答题', '当前原生接口不能安全处理这类提问。', '在电脑回答原题；手机仍可查看题目和同步处理状态。'),
    'invalid_answer': ('手机答题', '回答缺项、格式无效或不属于当前问题的允许选项。', '核对本轮题目，补全非私密答案后提交；私密输入留在电脑处理。'),
    'launcher_unverified': ('Desktop 对话', '当前 Desktop 原生启动入口未通过版本或文件核对。', '在电脑打开原对话；保留兼容保护，等待支持当前版本的连接器。'),
    'desktop_open_failed': ('Desktop 对话', '连接器无法启动 Desktop 的原对话链接。', '确认 Desktop 正常运行，再在手机点开原对话；不需要重新配对。'),
    'desktop_open_pending': ('Desktop 对话', '已请求 Desktop 打开原对话，但还没有取得可核对的处理端。', '稍后点击重试；不要重复发送消息。'),
    'invalid_open_request': ('Desktop 对话', '手机打开对话请求的参数不符合协议。', '升级配套手机端并重新进入对话；后台刷新不应携带打开请求。'),
    'input_required': ('输入', '必填的连接信息尚未填齐。', '填写 HTTPS 服务器、设备名和配对码后再试。'),
    'invalid_pairing_code': ('配对', '配对码格式不是规定的 12 位字符。', '核对手机上显示的原配对码；不要重复创建配对身份。'),
    'invalid_relay_url': ('配对', '服务器地址格式不符合连接器要求。', '填写原服务器的 HTTPS 地址和端口，不要附加账号、路径或查询参数。'),
    'https_required': ('配对', '服务器地址未使用 HTTPS。', '核对原服务器地址；不要改用明文连接。'),
    'invalid_device_name': ('配对', '设备名为空、过长或含控制字符。', '填写 1–80 个可见字符。'),
    'already_enrolled_or_pending': ('配对', '本机已有身份或尚未完成的申请。', '核对原申请和手机确认状态，不要重新配对或删除身份。'),
    'connection_unavailable': ('网络', '连接器未能确认服务器响应。', '核对原地址、网络和证书；保留现有身份，稍后重试连接。'),
    'task_channel_unavailable': ('通道', '服务器未授权本次任务通道或暂不可用。', '核对手机上的设备状态和原服务器，不要接管其他服务。'),
    'gateway_failed': ('通道', '本机适配器已报告失败或二进制校验无效。', '保留连接现场，核对受管安装和本机健康检查。'),
    'transport_failed': ('通道', '受管传输进程已报告失败或二进制校验无效。', '保留现场，核对受管传输配置和服务器状态。'),
    'previous_runtime_requires_review': ('保护', '前次运行没有完整安全退出证据。', '点击“核验并恢复”，核对旧通道进程、原任务与回执；通过后保留身份恢复连接。'),
    'runtime_cleanup_requires_review': ('保护', '断开时未能证明操作和两棵自有进程树均已清理。', '保留现场，先核对 Desktop 任务和进程清理证据。'),
    'identity_changed': ('保护', '连接期间身份内容与预期不一致。', '停止写操作并保留现场，由维护者核对身份变化。'),
    'credential_decryption_failed': ('身份', '本机 DPAPI 身份无法解密。', '使用原 Windows 账户核对身份文件和系统状态，不要重建或复制身份。'),
    'invalid_credential_file': ('身份', '身份文件格式或权限无法核对。', '保留原文件，由维护者检查；不要手动清空。'),
    'connector_already_running': ('本机', '此受管连接器已有运行实例。', '回到原窗口，不要启动第二个实例。'),
    'active_or_unverified_desktop_task': ('任务', '至少一条任务忙碌，或实时权限、回执、状态未得到完整确认。', '在 Desktop 核对任务并等待空闲；未知操作不得重试发送。'),
    'identity_or_runtime_requires_review': ('保护', '更新前身份或运行保护状态不满足安全条件。', '保留现场，由维护者核对运行保护和交易记录。'),
    'update_needs_review': ('更新', '持久更新事务处于需复核阶段。', '保留 data 与 state，核对交易 cause 和 error；不要直接重试或删除保护。'),
    'update_rolled_back': ('更新', '候选更新未通过验证，事务已确认回到旧版本。', '查看交易原因并核对旧版本实际连接状态；不要把回退当作新版安装成功。'),
    'rollback_context_unverified': ('保护', '旧版本、指针或回退阶段与已核验的恢复上下文不一致。', '保留原身份、回执、旧槽及事务，由维护者核对；不激活旧版或重放操作。'),
    'permissions_unverified': ('保护', 'Desktop 的任务访问权限未得到完整核验。', '保留身份和回执，核对 Desktop 权限及只读探针结果后再恢复。'),
    'update_record_unreadable': ('更新', '更新记录不能安全读取。', '保持连接禁用，保留记录，由维护者核对文件完整性。'),
    'update_operation_failed': ('更新', '更新操作没有可公开的具体错误码。', '保留原安装与交易记录，由维护者核对，不要猜测已经升级成功。'),
    'operation_failed': ('操作', '操作未完成，具体原因未经过安全分类。', '保留当前状态并查看已知错误码；不要根据未知文本重复配对或发送。'),
    'no_update_available': ('更新', '当前渠道没有更新版本。', '无需处理；可继续使用已验证的安装。'),
    'update_authentication_required': ('更新', '私有更新下载认证未通过。', '核对当前设备配对及登录状态，不要把凭据贴入诊断。'),
    'update_access_denied': ('更新', '服务器拒绝当前设备下载更新。', '在手机核对设备暂停或撤销状态。'),
    'untrusted_update_url': ('更新', '更新地址超出受信任来源。', '核对已配对中继和发布源，不要向新地址发送设备凭据。'),
    'invalid_release_signature': ('更新', '发布签名验证失败。', '停止安装并保留下载证据，由维护者核对签名源。'),
    'download_integrity_failed': ('更新', '下载内容与发布记录的摘要不一致。', '停止安装并重新核对受信任发布源。'),
    'insufficient_disk_space': ('更新', '本机可用空间不足以安全暂存和解压。', '释放磁盘空间后重新检查更新，保留现有安装。'),
    'candidate_transport_unverified': ('更新', '候选版本未在限定时间内证明传输通道健康。', '保留交易记录；核对候选健康阶段与原服务器，不能仅凭窗口或端口判定成功。'),
    'candidate_health_unverified': ('更新', '候选版本没有返回完整健康确认。', '核对交易 cause 和候选阶段；旧版是否可用需另行验证。'),
    'candidate_tree_exit_unconfirmed': ('保护', '候选进程树退出证据不足。', '保留原句柄与交易现场，不要按进程名清理。'),
    'activation_outcome_requires_review': ('保护', '激活指令可能已生效，但确认未完整返回。', '保留现场，核对当前窗口与交易记录，不要盲目回退或重发操作。'),
    'update_handoff_timeout': ('更新', '独立更新助手未及时确认接管。', '保留旧窗口和交易记录，由维护者核对交接状态。'),
    'update_handoff_unverified': ('更新', '独立更新助手交接确认无效。', '保留现场，勿把下载完成当成安装完成。'),
}

_NETWORK = {
    'relay_tls_certificate_expired': '服务器 TLS 证书已过有效期',
    'relay_tls_untrusted': '服务器 TLS 证书链未被本机信任',
    'relay_tls_handshake_failed': 'TLS 握手失败，但现有信息不能归因为证书不受信任',
    'relay_timeout': '连接服务器超时',
    'relay_dns_failed': '服务器名称解析失败',
    'relay_connection_refused': '目标地址拒绝连接',
    'relay_network_unavailable': '本机网络暂不可达',
    'relay_http_404': '服务器没有所需连接接口',
    'relay_http_401': '服务器拒绝当前设备身份',
    'relay_http_403': '服务器禁止当前设备操作',
    'relay_http_429': '服务器要求降低请求频率',
    'relay_http_5xx': '服务器发生内部错误或暂不可用',
    'relay_http_unexpected': '服务器返回未预期的 HTTP 状态',
}
for _code, _reason in _NETWORK.items():
    _COMMON[_code] = ('网络', _reason + '。',
                      '核对原服务器和网络状态；保留已配对身份，不要自动重配或删除保护。')
_COMMON['relay_tls_certificate_expired'] = (
    '网络', '服务器 TLS 证书已过有效期。',
    '联系服务器管理员续期证书并重载正在运行的 HTTPS 服务，再核对真实证书；不要关闭证书校验。')

# One verified meaning per source code. The shared action remains conservative:
# a code identifies the failed check, but does not prove that retry is safe.
_SPECIFIC_REASONS = dict(line.split('|', 1) for line in '''
ambiguous_runtime|运行实例与运行工厂同时被指定，所属关系不明确。
android_signer_already_exists|Android 签名器目标位置已有内容。
android_signer_create_failed|Android 签名器创建未完成。
apk_publisher_verification_required|APK 发布者证书连续性尚未验证。
archive_limits_exceeded|压缩包超过允许的文件数量、体积或解压限制。
bootstrap_launcher_missing|新安装缺少启动入口文件。
candidate_activation_unverified|候选激活回执未与预期一致。
candidate_control_closed|候选控制管道在确认前关闭。
candidate_health_timeout|候选未在规定时间内返回健康信息。
candidate_lifetime_transfer_failed|候选进程的生命周期所有权交接失败。
candidate_start_failed|候选进程未能在受管进程组中启动。
cgroup_tree_kill_required|Linux 进程组不支持必需的整树终止能力。
connected_identity_required|维护要求原设备身份处于已连接状态。
credential_encryption_failed|本机 DPAPI 无法加密身份内容。
delegated_cgroup_required|Linux 未提供可委派的受管进程组。
download_size_mismatch|下载字节数与签名发布记录不一致。
duplicate_update_state_key|更新状态 JSON 含重复字段名。
existing_receipts_required|维护所需的原发送回执目录不存在。
expired_release|发布记录尚未生效或已过有效期。
external_private_key_required|签名私钥不在要求的外部私有位置。
external_signing_directory_required|签名目录不是指定的外部私有目录。
forbidden_package_content|发布包包含禁止的文件类型或内容。
gui_command_unverified|维护助手收到的 GUI 指令无法核对。
gui_normal_exit_unverified|原 GUI 未能提供正常退出证据。
host_home_permissions_invalid|中继宿主目录权限不符合要求。
host_path_link_rejected|中继宿主路径经过符号链接或联接点。
host_peer_not_owner|中继本地控制连接的对端不是预期所有者。
host_socket_already_exists|中继本地控制套接字路径已被占用。
host_socket_path_too_long|中继本地套接字路径超过系统限制。
host_socket_permissions_invalid|中继本地控制套接字权限不符合要求。
incompatible_release|候选发布要求的兼容条件与本机不匹配。
installed_version_changed|已安装版本的清单摘要发生变化。
installed_version_missing|当前指针指向的版本目录不存在。
interrupted_update_requires_review|上次更新未处于已提交或已回退终态。
invalid_android_artifact|Android 发布产物未通过格式或身份校验。
invalid_android_signer|Android 签名器证书或密钥记录无效。
invalid_candidate_response|候选控制响应不是规定的短 JSON 对象。
invalid_configuration|本机连接端口或配置格式不符合要求。
invalid_gui_identity|维护核对的 GUI 身份与原记录不一致。
invalid_host_command|中继宿主控制命令格式或操作无效。
invalid_installation|安装配置或当前版本指针无效。
invalid_json|更新数据不是受限的有效 JSON。
invalid_lock_name|运行锁名称不符合受限格式。
invalid_package_manifest|包内清单格式或内容无效。
invalid_package_tree|已解压包的文件树无法完整验证。
invalid_pairing_response|配对认领响应缺少有效的服务端标识。
invalid_publish_metadata|发布元数据的结构或字段无效。
invalid_publish_path|发布产物目标路径不符合限制。
invalid_publisher_key|发布者公钥材料无效。
invalid_relay_response|中继返回的数据不是符合格式的 JSON 对象。
invalid_release_archive|下载的发布压缩包无法安全打开或验证。
invalid_release_catalog|版本目录记录格式或顺序无效。
invalid_release_envelope|签名信封缺少规定字段或编码无效。
invalid_release_metadata|已签名发布内容的字段或值无效。
invalid_release_response|下载端返回的发布记录大小或状态无效。
invalid_signing_key_id|签名密钥标识格式无效。
invalid_signing_origin|Android 签名源不符合受信任来源要求。
invalid_signing_vault|签名保管目录结构或权限无效。
invalid_terminal_state|服务器返回了未知的终端状态。
invalid_tls_context|更新下载的 TLS 上下文配置无效。
invalid_update_ledger|更新防回放账本结构无效。
invalid_version_pointer|版本指针路径不在受管版本目录中。
legacy_alias_probe_requires_review|旧版权限别名探针缺少可接受的复核证据。
legacy_identity_rejected|发现旧框架身份字段，本连接器拒绝接管。
linux_relay_host_required|此操作只能由受管 Linux 中继宿主执行。
linux_update_login_required|此下载流程要求 Linux 更新登录凭据。
maintenance_baseline_ledger_changed|维护基线的更新账本已变化。
maintenance_candidate_changed|维护候选内容与审查记录不一致。
maintenance_candidate_version_mismatch|维护候选版本与批准版本不一致。
maintenance_fence_changed|维护事务的持久围栏发生变化。
maintenance_home_changed|受管安装目录内容偏离维护审查快照。
maintenance_interruption_requires_review|维护事务中断，无法确认最终状态。
maintenance_old_version_mismatch|现用旧版本不等于维护审查时的版本。
maintenance_package_changed|维护所用安装包哈希发生变化。
maintenance_review_changed|维护审查记录或绑定内容发生变化。
maintenance_review_expired|维护审查允许的时间窗口已过。
maintenance_staging_changed|维护暂存记录或包内容发生变化。
maintenance_state_changed|维护期间受保护身份或配置发生变化。
maintenance_test_channel_required|维护流程要求指定的测试发布渠道。
maintenance_transaction_already_started|同一次维护交易已有开始记录。
maintenance_wait_timeout_required|维护等待参数缺少受限超时值。
managed_data_directory_required|任务数据目录不在受管安装的数据目录内。
managed_relay_binding_unverified|中继受管身份和安装绑定无法核对。
managed_relay_commit_unverified|中继切换后没有可信提交确认。
managed_relay_owner_lost|中继受管进程所有权已失去。
managed_relay_ready_invalid|中继候选的就绪回报不符合协议。
managed_relay_slot_invalid|中继候选版本槽验证失败。
managed_runtime_requires_run|受管运行环境只能在持有运行锁的连接循环中使用。
new_artifact_path_required|发布产物要求全新的未占用路径。
new_download_path_required|下载目标不是全新的未占用文件路径。
new_external_review_required|维护要求新的外部审查记录。
new_installation_home_required|新安装目标已有文件，不能覆盖旧现场。
new_signing_directory_required|签名保管目录目标已存在。
new_stage_required|更新暂存目标已存在，不能覆盖。
not_paired|本机尚无已确认的配对身份。
old_process_exit_unconfirmed|原进程及其子树未能证明完整退出。
old_process_owner_changed|原进程句柄对应的所有权已变化。
old_process_owner_unverified|原进程的所有权无法核对。
one_publisher_key_source_required|发布流程没有且仅有一个受信任的公钥来源。
original_gui_absence_unverified|原 GUI 是否完全退出尚无可靠证据。
package_file_changed|包内文件摘要与清单记录不一致。
package_file_set_changed|包内文件集合与清单记录不一致。
package_source_unavailable|发布所需的源文件不存在或不可读取。
paired_installation_required|维护要求现有受管安装已完成配对。
preservation_changed|更新前后的受保护身份、配置或任务数据摘要不一致。
private_directory_failed|本机私有目录无法按要求创建或保护。
private_feed_path_required|私有更新源路径不符合发布要求。
relay_host_response_invalid|中继宿主返回的控制响应格式无效。
relay_host_unavailable|中继宿主控制服务当前无法访问。
relay_response_too_large|中继响应超过允许的最大字节数。
release_replay_rejected|候选发布序号低于已接受的防回放账本。
reviewed_gui_not_running|审查绑定的原 GUI 未保持运行。
reviewer_alias_recovery_not_verified|旧权限别名恢复结果没有完整证据。
runtime_binary_changed|受管 Node 或传输二进制摘要发生变化。
same_sequence_replacement_rejected|相同发布序号对应了不同的签名绑定。
same_version_replacement_rejected|相同版本号对应了不同的发布摘要。
signing_provision_preflight_failed|签名保管区创建前检查未通过。
signing_provision_record_exists|签名初始化记录已经存在。
signing_provision_requires_review|签名初始化未能确认完成，需复核。
signing_vault_create_failed|签名保管区创建失败。
staged_package_changed|暂存包或清单与已签名发布记录不一致。
staged_package_missing|暂存目录中找不到对应的已验证安装包。
state_directory_not_owned|身份目录所有者不是当前 Windows 用户。
state_path_link_rejected|身份路径经过符号链接或目录联接点。
terminal_identity_changed|服务器返回的终端标识与已有身份不一致。
unfinished_update_requires_review|存在尚未完成的更新交易。
unsafe_archive_entry|压缩包内存在不安全的条目类型或路径。
unsafe_archive_path|压缩包条目路径超出安全范围。
unsafe_local_path|更新本地路径经过不受允许的链接或位置。
unsafe_package_tree|已解压文件树包含不安全的链接或文件类型。
unsupported_package_component|发布包组件不是允许的目标组件。
unsupported_update_component|更新凭据请求的组件不受支持。
unsupported_update_platform|当前系统不支持此更新进程隔离方式。
untrusted_release_key|签名信封使用的密钥不在信任记录中。
update_command_too_large|发送给候选的控制命令超过长度上限。
update_device_identity_unavailable|更新无法安全读取现有设备身份。
update_device_not_paired|当前设备尚未具备更新下载所需的已配对身份。
update_download_failed|更新包下载在网络或文件操作中失败。
update_fetch_failed|发布信封读取失败。
update_login_changed|更新登录记录在操作期间发生变化。
update_login_exists_logout_first|已有更新登录记录，不能覆盖登录。
update_login_failed|更新登录请求未完成。
update_login_input_invalid|更新登录输入格式无效。
update_login_permissions_invalid|更新登录文件权限不符合要求。
update_login_record_invalid|更新登录文件内容或结构无效。
update_login_response_invalid|更新登录响应格式无效。
update_or_application_already_running|更新锁或应用锁已被另一实例持有。
update_path_link_rejected|更新状态路径经过符号链接或目录联接点。
update_pointer_changed|更新过程中现用版本指针与预期不一致。
update_redirect_rejected|更新请求被重定向到未获允许的位置。
update_state_too_large|更新状态文件超过允许大小。
update_trust_not_provisioned|安装缺少可信发布公钥或更新源配置。
update_unconfigured|更新信任配置缺少所需来源或公钥。
use_existing_device_pairing|此更新流程必须使用已有设备配对身份。
use_windows_install_button|Windows 更新必须经受管 GUI 安装入口启动。
windows_identity_unavailable|无法取得当前 Windows 用户身份标识。
windows_lifetime_transfer_required|Windows 候选进程缺少必需的生命周期交接能力。
windows_owner_required|更新进程缺少受管 Windows 原进程所有者。
windows_required|此身份保护功能只支持 Windows。
windows_signing_required|此发布签名操作需要 Windows 签名环境。
wrong_installation_platform|新安装包平台与当前系统不匹配。
wrong_installed_component|安装记录中的组件与目标组件不一致。
wrong_release_target|发布内容的产品、组件、平台或渠道不匹配。
'''.strip().splitlines())


def _literal_values(node):
    if isinstance(node, ast.Constant) and isinstance(node.value, str):
        return (node.value,)
    if isinstance(node, ast.IfExp):
        return _literal_values(node.body) + _literal_values(node.orelse)
    return ()


def _source_index():
    """Find real line numbers in trusted source only; never inspect runtime data."""
    root = Path(__file__).resolve().parents[1]
    found = {}

    class Visitor(ast.NodeVisitor):
        def __init__(self, relative):
            self.relative = relative
            self.scope = []

        def visit_ClassDef(self, node):
            self.scope.append(node.name)
            self.generic_visit(node)
            self.scope.pop()

        def visit_FunctionDef(self, node):
            self.scope.append(node.name)
            self.generic_visit(node)
            self.scope.pop()

        visit_AsyncFunctionDef = visit_FunctionDef

        def visit_Call(self, node):
            if (isinstance(node.func, ast.Name)
                    and node.func.id in ('ConnectorError', 'UpdateError', '_fail')
                    and node.args):
                for code in _literal_values(node.args[0]):
                    if code in _DECLARED_CODES and code not in found:
                        found[code] = {'path': self.relative,
                                       'symbol': '.'.join(self.scope) or '<module>',
                                       'line': node.lineno, 'kind': 'raise'}
            self.generic_visit(node)

        def visit_Constant(self, node):
            # GUI/status classifier codes may be returned as literals rather
            # than raised. Only report a line that really contains that code.
            if isinstance(node.value, str) and node.value in _EXTRA_CODES and node.value not in found:
                found[node.value] = {'path': self.relative,
                                     'symbol': '.'.join(self.scope) or '<module>',
                                     'line': node.lineno, 'kind': 'classification'}

    for relative in _SOURCE_FILES:
        try:
            tree = ast.parse((root / relative).read_text(encoding='utf-8'))
        except (OSError, UnicodeError, SyntaxError):
            continue
        Visitor(relative).visit(tree)
    return found


_SOURCES = _source_index()


def _classification_anchor(relative, symbol):
    """Point at an actual classifier function, without inventing a code line."""
    try:
        tree = ast.parse((Path(__file__).resolve().parents[1] / relative).read_text(encoding='utf-8'))
    except (OSError, UnicodeError, SyntaxError):
        return None

    class Finder(ast.NodeVisitor):
        def __init__(self):
            self.scope = []
            self.line = None

        def visit_ClassDef(self, node):
            self.scope.append(node.name)
            self.generic_visit(node)
            self.scope.pop()

        def visit_FunctionDef(self, node):
            self.scope.append(node.name)
            if '.'.join(self.scope) == symbol:
                self.line = node.lineno
            self.generic_visit(node)
            self.scope.pop()

        visit_AsyncFunctionDef = visit_FunctionDef

    finder = Finder()
    finder.visit(tree)
    return ({'path': relative, 'symbol': symbol, 'line': finder.line,
             'kind': 'classification'} if finder.line is not None else None)


for _code in _EXTRA_CODES:
    if _code not in _SOURCES:
        if _code.startswith('relay_http_'):
            _anchor = ('windows/vendor/connector_core.py', 'RelayClient.failure_code')
        elif _code.startswith('relay_'):
            _anchor = ('windows/vendor/connector_core.py', 'network_error_code')
        elif _code in ('gateway_failed', 'transport_failed'):
            _anchor = ('windows/error_catalog.py', 'report_codes')
        elif _code.startswith('update_'):
            _anchor = ('windows/gui.py', '_Window._poll_update')
        else:
            _anchor = ('windows/gui.py', '_Window._work')
        _location = _classification_anchor(*_anchor)
        if _location is not None:
            _SOURCES[_code] = _location


_SOURCES['task_channel_unavailable'] = _classification_anchor('windows/controller.py', 'Controller._runtime')

def _category(code, source):
    path = source['path'] if source else ''
    if code.startswith(('relay_', 'connection_')):
        return '网络'
    if code.startswith(('previous_runtime', 'runtime_cleanup', 'identity_', 'credential_',
                        'state_', 'old_process_', 'candidate_tree_', 'activation_outcome')):
        return '保护'
    if path.startswith('windows/vendor/connector_core.py'):
        return '配对与身份'
    if path.startswith('windows/controller.py'):
        return '本机连接'
    if path.startswith('updates/security.py') or path.startswith('updates/credentials.py'):
        return '更新信任与下载'
    if path.startswith('updates/android_') or path.startswith('updates/signing.py') or path.startswith('updates/publish.py'):
        return '发布工具'
    if path.startswith('updates/relay_host.py'):
        return '中继维护'
    return '更新与维护'


# Optional operator tools are intentionally absent from the client package.
# Keep file/function pointers, without inventing installed-source line numbers.
_REPOSITORY_HINTS = {
    'android_signer_already_exists': {'path': 'updates/android_publisher.py', 'symbol': 'create_android_signer', 'line': None, 'kind': 'repository'},
    'android_signer_create_failed': {'path': 'updates/android_publisher.py', 'symbol': 'create_android_signer', 'line': None, 'kind': 'repository'},
    'apk_publisher_verification_required': {'path': 'updates/publish.py', 'symbol': 'sign', 'line': None, 'kind': 'repository'},
    'connected_identity_required': {'path': 'updates/windows_maintenance.py', 'symbol': 'binding', 'line': None, 'kind': 'repository'},
    'existing_receipts_required': {'path': 'updates/windows_maintenance.py', 'symbol': 'binding', 'line': None, 'kind': 'repository'},
    'external_private_key_required': {'path': 'updates/publish.py', 'symbol': 'sign', 'line': None, 'kind': 'repository'},
    'external_signing_directory_required': {'path': 'updates/signing.py', 'symbol': '_directory', 'line': None, 'kind': 'repository'},
    'gui_command_unverified': {'path': 'updates/windows_maintenance.py', 'symbol': 'command_args', 'line': None, 'kind': 'repository'},
    'gui_normal_exit_unverified': {'path': 'updates/windows_maintenance.py', 'symbol': 'normal_exit', 'line': None, 'kind': 'repository'},
    'host_home_permissions_invalid': {'path': 'updates/relay_host.py', 'symbol': 'request', 'line': None, 'kind': 'repository'},
    'host_path_link_rejected': {'path': 'updates/relay_host.py', 'symbol': 'request', 'line': None, 'kind': 'repository'},
    'host_peer_not_owner': {'path': 'updates/relay_host.py', 'symbol': 'request', 'line': None, 'kind': 'repository'},
    'host_socket_already_exists': {'path': 'updates/relay_host.py', 'symbol': 'RelayHost.serve', 'line': None, 'kind': 'repository'},
    'host_socket_path_too_long': {'path': 'updates/relay_host.py', 'symbol': 'RelayHost.serve', 'line': None, 'kind': 'repository'},
    'host_socket_permissions_invalid': {'path': 'updates/relay_host.py', 'symbol': 'request', 'line': None, 'kind': 'repository'},
    'invalid_android_artifact': {'path': 'updates/publish.py', 'symbol': 'sign', 'line': None, 'kind': 'repository'},
    'invalid_android_signer': {'path': 'updates/android_publisher.py', 'symbol': '_github_values', 'line': None, 'kind': 'repository'},
    'invalid_gui_identity': {'path': 'updates/windows_maintenance.py', 'symbol': 'verify_gui', 'line': None, 'kind': 'repository'},
    'invalid_host_command': {'path': 'updates/relay_host.py', 'symbol': 'request', 'line': None, 'kind': 'repository'},
    'invalid_publish_metadata': {'path': 'updates/publish.py', 'symbol': 'sign', 'line': None, 'kind': 'repository'},
    'invalid_publish_path': {'path': 'updates/publish.py', 'symbol': 'sign', 'line': None, 'kind': 'repository'},
    'invalid_publisher_key': {'path': 'updates/publish.py', 'symbol': 'sign', 'line': None, 'kind': 'repository'},
    'invalid_release_catalog': {'path': 'updates/publish.py', 'symbol': '_unique', 'line': None, 'kind': 'repository'},
    'invalid_signing_key_id': {'path': 'updates/signing.py', 'symbol': '_directory', 'line': None, 'kind': 'repository'},
    'invalid_signing_origin': {'path': 'updates/android_publisher.py', 'symbol': '_trust', 'line': None, 'kind': 'repository'},
    'invalid_signing_vault': {'path': 'updates/signing.py', 'symbol': '_read_file', 'line': None, 'kind': 'repository'},
    'legacy_alias_probe_requires_review': {'path': 'updates/windows_maintenance.py', 'symbol': 'AliasRecoveryDriver.start', 'line': None, 'kind': 'repository'},
    'linux_relay_host_required': {'path': 'updates/relay_host.py', 'symbol': 'RelayHost.__init__', 'line': None, 'kind': 'repository'},
    'maintenance_baseline_ledger_changed': {'path': 'updates/windows_maintenance.py', 'symbol': 'resume_evidence', 'line': None, 'kind': 'repository'},
    'maintenance_candidate_changed': {'path': 'updates/windows_maintenance.py', 'symbol': 'resume_evidence', 'line': None, 'kind': 'repository'},
    'maintenance_candidate_version_mismatch': {'path': 'updates/windows_maintenance.py', 'symbol': 'bundle', 'line': None, 'kind': 'repository'},
    'maintenance_fence_changed': {'path': 'updates/windows_maintenance.py', 'symbol': 'MaintenanceTransaction.__init__', 'line': None, 'kind': 'repository'},
    'maintenance_home_changed': {'path': 'updates/windows_maintenance.py', 'symbol': 'apply_review', 'line': None, 'kind': 'repository'},
    'maintenance_interruption_requires_review': {'path': 'updates/windows_maintenance.py', 'symbol': 'MaintenanceTransaction.recover', 'line': None, 'kind': 'repository'},
    'maintenance_old_version_mismatch': {'path': 'updates/windows_maintenance.py', 'symbol': 'bundle', 'line': None, 'kind': 'repository'},
    'maintenance_package_changed': {'path': 'updates/windows_maintenance.py', 'symbol': 'bundle', 'line': None, 'kind': 'repository'},
    'maintenance_review_changed': {'path': 'updates/windows_maintenance.py', 'symbol': 'validate_review_integrity', 'line': None, 'kind': 'repository'},
    'maintenance_review_expired': {'path': 'updates/windows_maintenance.py', 'symbol': 'validate_review', 'line': None, 'kind': 'repository'},
    'maintenance_staging_changed': {'path': 'updates/windows_maintenance.py', 'symbol': 'resume_evidence', 'line': None, 'kind': 'repository'},
    'maintenance_state_changed': {'path': 'updates/windows_maintenance.py', 'symbol': 'make_review', 'line': None, 'kind': 'repository'},
    'maintenance_test_channel_required': {'path': 'updates/windows_maintenance.py', 'symbol': 'bundle', 'line': None, 'kind': 'repository'},
    'maintenance_transaction_already_started': {'path': 'updates/windows_maintenance.py', 'symbol': 'resume_evidence', 'line': None, 'kind': 'repository'},
    'maintenance_wait_timeout_required': {'path': 'updates/windows_maintenance.py', 'symbol': 'resume_evidence', 'line': None, 'kind': 'repository'},
    'managed_relay_binding_unverified': {'path': 'updates/relay_host.py', 'symbol': 'RelayHost._start_initial', 'line': None, 'kind': 'repository'},
    'managed_relay_commit_unverified': {'path': 'updates/relay_host.py', 'symbol': 'RelayHost._apply', 'line': None, 'kind': 'repository'},
    'managed_relay_owner_lost': {'path': 'updates/relay_host.py', 'symbol': 'RelayHost._apply', 'line': None, 'kind': 'repository'},
    'managed_relay_ready_invalid': {'path': 'updates/relay_host.py', 'symbol': 'RelayHost._start_initial', 'line': None, 'kind': 'repository'},
    'managed_relay_slot_invalid': {'path': 'updates/relay_host.py', 'symbol': 'RelayHost._start_initial', 'line': None, 'kind': 'repository'},
    'new_artifact_path_required': {'path': 'updates/publish.py', 'symbol': 'pack', 'line': None, 'kind': 'repository'},
    'new_external_review_required': {'path': 'updates/windows_maintenance.py', 'symbol': 'make_review', 'line': None, 'kind': 'repository'},
    'new_signing_directory_required': {'path': 'updates/signing.py', 'symbol': 'create_vault', 'line': None, 'kind': 'repository'},
    'one_publisher_key_source_required': {'path': 'updates/publish.py', 'symbol': 'sign', 'line': None, 'kind': 'repository'},
    'original_gui_absence_unverified': {'path': 'updates/windows_maintenance.py', 'symbol': 'verify_clean_exit', 'line': None, 'kind': 'repository'},
    'package_source_unavailable': {'path': 'updates/publish.py', 'symbol': '_files', 'line': None, 'kind': 'repository'},
    'paired_installation_required': {'path': 'updates/windows_maintenance.py', 'symbol': 'binding', 'line': None, 'kind': 'repository'},
    'private_feed_path_required': {'path': 'updates/publish.py', 'symbol': 'sign', 'line': None, 'kind': 'repository'},
    'relay_host_response_invalid': {'path': 'updates/relay_host.py', 'symbol': 'request', 'line': None, 'kind': 'repository'},
    'relay_host_unavailable': {'path': 'updates/relay_host.py', 'symbol': 'request', 'line': None, 'kind': 'repository'},
    'reviewed_gui_not_running': {'path': 'updates/windows_maintenance.py', 'symbol': 'verify_gui', 'line': None, 'kind': 'repository'},
    'reviewer_alias_recovery_not_verified': {'path': 'updates/windows_maintenance.py', 'symbol': 'alias_probe', 'line': None, 'kind': 'repository'},
    'signing_provision_preflight_failed': {'path': 'updates/android_publisher.py', 'symbol': 'provision_github', 'line': None, 'kind': 'repository'},
    'signing_provision_record_exists': {'path': 'updates/android_publisher.py', 'symbol': 'provision_github', 'line': None, 'kind': 'repository'},
    'signing_provision_requires_review': {'path': 'updates/android_publisher.py', 'symbol': 'provision_github', 'line': None, 'kind': 'repository'},
    'signing_vault_create_failed': {'path': 'updates/signing.py', 'symbol': 'create_vault', 'line': None, 'kind': 'repository'},
    'unsupported_package_component': {'path': 'updates/publish.py', 'symbol': '_files', 'line': None, 'kind': 'repository'},
    'windows_signing_required': {'path': 'updates/signing.py', 'symbol': '_windows', 'line': None, 'kind': 'repository'},
}

def _entry(code):
    source = _SOURCES.get(code) or _REPOSITORY_HINTS.get(code)
    category = _category(code, source)
    summary = _COMMON.get(code)
    if summary is None:
        reason = _SPECIFIC_REASONS.get(code)
        summary = (category,
                   reason or '此代码表示对应模块的校验或操作未通过；现有信息不足以判定更细的原因。',
                   '保留当前身份、安装与交易记录，由维护者按源码位置核对；不要猜测可以重配或清理。')
    title = summary[1].rstrip('。') if code in _COMMON or code in _SPECIFIC_REASONS else category + '需核对'
    return {'title': title, 'category': summary[0], 'reason': summary[1], 'action': summary[2],
            'source': source}


# These JS-only failures also appear in the phone. Point to their real packaged source lines.
_JS_ERROR_LOCATIONS = {
    'question_changed': ('src/questions.js', 'validateAnswer'),
    'question_unsupported': ('src/service.js', 'answer'),
    'invalid_answer': ('src/questions.js', 'validateAnswer'),
    'launcher_unverified': ('src/desktop-open.js', 'openNativeConversation'),
    'desktop_open_failed': ('src/desktop-open.js', 'openNativeConversation'),
    'desktop_open_pending': ('src/desktop-open.js', 'ensureDesktopConversation'),
    'invalid_open_request': ('src/service.js', 'httpServer'),
}
for _code, (_path, _symbol) in _JS_ERROR_LOCATIONS.items():
    try:
        _lines = (Path(__file__).resolve().parents[1] / _path).read_text(encoding='utf-8').splitlines()
        _line = next(i for i, value in enumerate(_lines, 1) if "'" + _code + "'" in value)
        _SOURCES[_code] = {'path': _path, 'symbol': _symbol, 'line': _line, 'kind': 'raise'}
    except (OSError, StopIteration):
        pass
CATALOG = {code: _entry(code) for code in sorted(_DECLARED_CODES | _EXTRA_CODES | _JS_ERROR_LOCATIONS.keys())}


def safe_code(value):
    """Allow known identifiers only; never echo exceptions or server bodies."""
    return value if type(value) is str and _CODE_SHAPE.fullmatch(value) and value in CATALOG else 'operation_failed'


def describe(code):
    """Return a complete Chinese explanation with a verified source location."""
    known = safe_code(code)
    entry = CATALOG[known]
    source = entry['source']
    if source is None:
        location = '源码位置：当前版本未定位到实际抛出点。'
    else:
        line = f":{source['line']}" if source['line'] is not None else ''
        kind = ('（本包不含此维护工具源码；在项目仓库按文件和函数查找）' if source['kind'] == 'repository' else
                '（分类/展示入口，非原始抛错点）' if source['kind'] == 'classification' else '')
        location = f"源码位置：{source['path']}{line} · {source['symbol']}{kind}。"
    return f"错误码：{known}\n分类：{entry['category']}\n原因：{entry['reason']}\n处理：{entry['action']}\n{location}"


def report_codes(report, live=False):
    """Project observed status codes, without calling an unverified channel a fault."""
    if not isinstance(report, dict):
        return []
    codes = []
    if report.get('runtimeNeedsReview') is True and not live:
        codes.append('previous_runtime_requires_review')
    last = report.get('lastError')
    if type(last) is str and last:
        codes.append(safe_code(last))
    if live and report.get('gateway') in ('failed', 'invalid_binary'):
        codes.append('gateway_failed')
    if live and report.get('transport') in ('failed', 'invalid_binary'):
        codes.append('transport_failed')
    return list(dict.fromkeys(codes))
