package dev.h516.update

import android.content.Context
import java.security.KeyStore

/** 公开版只清理旧更新授权，不读取或解密令牌，也不触碰档案资料。 */
internal fun clearLegacyUpdateAuthorization(context: Context) {
    context.getSharedPreferences(UpdateConfig.PACKAGE + "_update_authorization", Context.MODE_PRIVATE)
        .edit().clear().apply()
    runCatching {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .deleteEntry(UpdateConfig.PACKAGE + "-update-v1")
    }
}
