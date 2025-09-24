import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.darkColors
import androidx.compose.ui.Modifier
import androidx.compose.ui.uikit.OnFocusBehavior
import androidx.compose.ui.window.ComposeUIViewController
import com.test.kostra.appsample.SampleScreen
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    return ComposeUIViewController(
        configure = {
            onFocusBehavior = OnFocusBehavior.DoNothing
        },
        content = {
            MaterialTheme(colors = darkColors()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SampleScreen()
                }
            }
        }
    )
}
