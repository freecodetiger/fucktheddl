package com.zpc.fucktheddl.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutRepositoryInfoTest {
    @Test
    fun repositorySignatureUsesExpectedUrlAndPrivacyCopy() {
        assertEquals("https://github.com/freecodetiger/fucktheddl", AboutRepositoryInfo.RepositoryUrl)
        assertTrue(AboutRepositoryInfo.signatureText.contains("freecodetiger"))
        assertTrue(AboutRepositoryInfo.signatureText.contains("保存在本机"))
        assertTrue(AboutRepositoryInfo.signatureText.contains("不依赖独立业务服务器"))
        assertTrue(AboutRepositoryInfo.signatureText.contains("保护用户隐私"))
    }
}
