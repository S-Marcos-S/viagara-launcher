// SPDX-License-Identifier: GPL-3.0-or-later
package dev.viagaralauncher.ui.settings

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.viagaralauncher.root.AppUninstallManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UninstalledSystemApp(
    val packageName: String,
    val name: String,
)

@Composable
fun SystemAppRestoreDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<UninstalledSystemApp>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            // MATCH_UNINSTALLED_PACKAGES flag includes apps that were uninstalled for the current user
            // but their code and data are still kept (which is the case for system apps unless completely removed via root).
            val installedPackages = pm.getInstalledPackages(0).map { it.packageName }.toSet()
            
            // Wait, we need all system apps including uninstalled.
            @Suppress("DEPRECATION")
            val allPackages = pm.getInstalledPackages(PackageManager.MATCH_UNINSTALLED_PACKAGES)
            
            val uninstalledSystemApps = mutableListOf<UninstalledSystemApp>()
            
            for (pkg in allPackages) {
                val appInfo = pkg.applicationInfo
                if (appInfo != null && (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
                    if (!installedPackages.contains(pkg.packageName)) {
                        val name = pm.getApplicationLabel(appInfo).toString()
                        uninstalledSystemApps.add(UninstalledSystemApp(pkg.packageName, name))
                    }
                }
            }
            
            withContext(Dispatchers.Main) {
                apps = uninstalledSystemApps.sortedBy { it.name }
                isLoading = false
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Restaurar Apps do Sistema",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isLoading) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (apps.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Nenhum aplicativo do sistema desinstalado encontrado.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(apps) { app ->
                            var isRestoring by remember { mutableStateOf(false) }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = app.name, style = MaterialTheme.typography.bodyLarge)
                                    Text(text = app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (isRestoring) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                } else {
                                    TextButton(onClick = {
                                        isRestoring = true
                                        scope.launch {
                                            val result = AppUninstallManager.restoreSystemAppViaRoot(app.packageName)
                                            isRestoring = false
                                            if (result.isSuccess) {
                                                Toast.makeText(context, "${app.name} restaurado com sucesso.", Toast.LENGTH_SHORT).show()
                                                apps = apps.filter { it.packageName != app.packageName }
                                            } else {
                                                Toast.makeText(context, "Falha ao restaurar ${app.name}.", Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    }) {
                                        Text("Restaurar")
                                    }
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Fechar")
                    }
                }
            }
        }
    }
}
