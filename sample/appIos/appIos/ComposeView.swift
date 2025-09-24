import SwiftUI
import shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        Main_iosKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

struct ComposeView_Previews: PreviewProvider {
	static var previews: some View {
		ComposeView().ignoresSafeArea(.all)
	}
}
