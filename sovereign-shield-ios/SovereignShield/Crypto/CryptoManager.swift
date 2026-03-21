import Foundation
import CryptoKit
import Security

class CryptoManager {
    static let shared = CryptoManager()

    private let privateKeyTag = "com.sovereign.shield.wireguard.privatekey"
    private let publicKeyTag = "com.sovereign.shield.wireguard.publickey"
    private let presharedKeyTag = "com.sovereign.shield.wireguard.presharedkey"
    private let assignedIPTag = "com.sovereign.shield.wireguard.assignedip"
    private let serverPublicKeyTag = "com.sovereign.shield.wireguard.serverpublickey"
    private let endpointTag = "com.sovereign.shield.wireguard.endpoint"

    private init() {}

    // MARK: - Key Generation

    func generateKeyPair() -> (privateKey: String, publicKey: String) {
        let privateKey = Curve25519.KeyAgreement.PrivateKey()
        let privBase64 = privateKey.rawRepresentation.base64EncodedString()
        let pubBase64 = privateKey.publicKey.rawRepresentation.base64EncodedString()
        return (privBase64, pubBase64)
    }

    func generateAndStoreKeyPair() -> String {
        let (privateKey, publicKey) = generateKeyPair()
        saveToKeychain(key: privateKeyTag, value: privateKey)
        saveToKeychain(key: publicKeyTag, value: publicKey)
        return publicKey
    }

    func rotateKeys() -> String {
        deleteFromKeychain(key: privateKeyTag)
        deleteFromKeychain(key: publicKeyTag)
        return generateAndStoreKeyPair()
    }

    // MARK: - Key Access

    var privateKey: String? {
        loadFromKeychain(key: privateKeyTag)
    }

    var publicKey: String? {
        loadFromKeychain(key: publicKeyTag)
    }

    var hasKeyPair: Bool {
        privateKey != nil && publicKey != nil
    }

    // MARK: - Server Config Storage

    func storePresharedKey(_ key: String) {
        saveToKeychain(key: presharedKeyTag, value: key)
    }

    var presharedKey: String? {
        loadFromKeychain(key: presharedKeyTag)
    }

    func storeAssignedIP(_ ip: String) {
        saveToKeychain(key: assignedIPTag, value: ip)
    }

    var assignedIP: String? {
        loadFromKeychain(key: assignedIPTag)
    }

    func storeServerPublicKey(_ key: String) {
        saveToKeychain(key: serverPublicKeyTag, value: key)
    }

    var serverPublicKey: String? {
        loadFromKeychain(key: serverPublicKeyTag)
    }

    func storeEndpoint(_ endpoint: String) {
        saveToKeychain(key: endpointTag, value: endpoint)
    }

    var endpoint: String? {
        loadFromKeychain(key: endpointTag)
    }

    func clearAllKeys() {
        deleteFromKeychain(key: privateKeyTag)
        deleteFromKeychain(key: publicKeyTag)
        deleteFromKeychain(key: presharedKeyTag)
        deleteFromKeychain(key: assignedIPTag)
        deleteFromKeychain(key: serverPublicKeyTag)
        deleteFromKeychain(key: endpointTag)
    }

    // MARK: - Keychain Helpers

    private func saveToKeychain(key: String, value: String) {
        guard let data = value.data(using: .utf8) else { return }

        // Delete existing
        let deleteQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecAttrService as String: "com.sovereign.shield"
        ]
        SecItemDelete(deleteQuery as CFDictionary)

        // Add new
        let addQuery: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecAttrService as String: "com.sovereign.shield",
            kSecValueData as String: data,
            kSecAttrAccessible as String: kSecAttrAccessibleAfterFirstUnlock
        ]
        SecItemAdd(addQuery as CFDictionary, nil)
    }

    private func loadFromKeychain(key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecAttrService as String: "com.sovereign.shield",
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: AnyObject?
        let status = SecItemCopyMatching(query as CFDictionary, &result)

        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private func deleteFromKeychain(key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecAttrService as String: "com.sovereign.shield"
        ]
        SecItemDelete(query as CFDictionary)
    }
}
