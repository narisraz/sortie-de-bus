import SwiftUI

@main
struct SortieDeBusApp: App {
    var body: some Scene {
        WindowGroup {
            GameView()
            #if os(macOS)
                .frame(minWidth: 380, idealWidth: 430, minHeight: 700, idealHeight: 860)
            #endif
        }
        #if os(macOS)
        .windowResizability(.contentMinSize)
        #endif
    }
}
