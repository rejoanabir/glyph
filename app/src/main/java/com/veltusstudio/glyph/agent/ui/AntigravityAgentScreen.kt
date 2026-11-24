package com.veltusstudio.glyph.agent.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AntigravityAgentScreen(
    workspaceDir: String,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AntigravityMainWorkspace(
        initialWorkspace = workspaceDir,
        modifier = modifier
    )
}
