package com.zpc.fucktheddl.ui

import java.nio.file.Path
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerFieldRegressionTest {
    @Test
    fun dateAndTimePickerFieldsUseAnOverlayTapTarget() {
        val source = Path.of("src/main/java/com/zpc/fucktheddl/ui/FuckTheDdlApp.kt")
            .toFile()
            .readText()

        assertTrue(source.contains("private fun BoxScope.PickerFieldTapTarget("))
        assertTrue(source.contains("PickerFieldTapTarget(onClick = { showDialog = true })"))
    }
}
