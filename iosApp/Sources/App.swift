// iOS — Todo el Swift del proyecto. Quince lineas, y no deberian crecer.
//
// LA REGLA: aqui solo esta lo que Apple exige para que exista una app —un punto
// de entrada y una ventana— y la costura que enseña la interfaz de Compose. Lo
// que decida algo va en Kotlin, en `:shared`, donde se puede probar. Si algun
// dia este fichero tiene un `if`, algo se ha colado por el sitio equivocado.

import SwiftUI
import UIKit
import Compartido

/// Envuelve el `UIViewController` que devuelve Kotlin para que SwiftUI lo pinte.
///
/// `PuntoDeEntradaIOSKt` no es un nombre elegido: Kotlin agrupa las funciones
/// sueltas de un fichero en una clase llamada como el fichero mas `Kt`. Si se
/// renombra `PuntoDeEntradaIOS.kt`, esta linea deja de compilar.
struct VistaCompartida: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        PuntoDeEntradaIOSKt.puntoDeEntrada()
    }

    func updateUIViewController(_ controlador: UIViewController, context: Context) {}
}

@main
struct LectorApp: App {
    var body: some Scene {
        WindowGroup {
            // `ignoresSafeArea` porque el lector va a pantalla completa: sin
            // esto, una pagina de comic sale con franjas arriba y abajo.
            VistaCompartida().ignoresSafeArea()
        }
    }
}
