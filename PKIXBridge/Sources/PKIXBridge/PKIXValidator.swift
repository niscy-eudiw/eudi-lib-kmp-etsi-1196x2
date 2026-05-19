/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import Foundation
import Security

@objc public final class PKIXValidator: NSObject {

    @objc public static let trustAnchorDerKey: String = "trustAnchorDer"

    private let configuration: PKIXConfiguration

    @objc public init(configuration: PKIXConfiguration) {
        self.configuration = configuration
        super.init()
    }

    @objc public override convenience init() {
        self.init(configuration: PKIXConfiguration())
    }

    @objc public func validateCertificateChain(
        leafCertificate: NSData,
        intermediateCertificates: [NSData],
        trustAnchors: [NSData],
        completion: @escaping ([String: Any]?, Error?) -> Void
    ) {
        let safeCompletion: ([String: Any]?, Error?) -> Void = { result, error in
            DispatchQueue.main.async { completion(result, error) }
        }

        guard !trustAnchors.isEmpty else {
            safeCompletion(nil, PKIXBridgeError.invalidCertificate(reason: "Trust anchors must not be empty").asNSError())
            return
        }

        let leafCert: SecCertificate
        do {
            leafCert = try decodeCertificate(leafCertificate, label: "leaf")
        } catch {
            safeCompletion(nil, error)
            return
        }

        let intermediateCerts: [SecCertificate]
        let anchorCerts: [SecCertificate]
        do {
            intermediateCerts = try intermediateCertificates.enumerated().map { index, data in
                try decodeCertificate(data, label: "intermediate[\(index)]")
            }
            anchorCerts = try trustAnchors.enumerated().map { index, data in
                try decodeCertificate(data, label: "trustAnchor[\(index)]")
            }
        } catch {
            safeCompletion(nil, error)
            return
        }

        let allCertificates = [leafCert] + intermediateCerts
        let policies = buildPolicies()

        var trust: SecTrust?
        let createStatus = SecTrustCreateWithCertificates(allCertificates as CFArray, policies as CFArray, &trust)
        guard createStatus == errSecSuccess, let secTrust = trust else {
            safeCompletion(nil, PKIXBridgeError.trustEvaluationFailed(underlying: osStatusError(createStatus)).asNSError())
            return
        }

        let anchorsStatus = SecTrustSetAnchorCertificates(secTrust, anchorCerts as CFArray)
        guard anchorsStatus == errSecSuccess else {
            safeCompletion(nil, PKIXBridgeError.trustEvaluationFailed(underlying: osStatusError(anchorsStatus)).asNSError())
            return
        }
        let onlyStatus = SecTrustSetAnchorCertificatesOnly(secTrust, true)
        guard onlyStatus == errSecSuccess else {
            safeCompletion(nil, PKIXBridgeError.trustEvaluationFailed(underlying: osStatusError(onlyStatus)).asNSError())
            return
        }

        let queue = DispatchQueue.global(qos: .userInitiated)
        let evalStatus = SecTrustEvaluateAsyncWithError(secTrust, queue) { resultTrust, isTrusted, cfError in
            if !isTrusted {
                let underlying: Error? = cfError.map { $0 as Error }
                safeCompletion(nil, PKIXBridgeError.trustEvaluationFailed(underlying: underlying).asNSError())
                return
            }
            guard let anchor = Self.matchedAnchor(in: resultTrust) else {
                safeCompletion(nil, PKIXBridgeError.noTrustAnchorMatched.asNSError())
                return
            }
            let anchorDer = SecCertificateCopyData(anchor) as NSData
            safeCompletion([PKIXValidator.trustAnchorDerKey: anchorDer], nil)
        }
        if evalStatus != errSecSuccess {
            safeCompletion(nil, PKIXBridgeError.trustEvaluationFailed(underlying: osStatusError(evalStatus)).asNSError())
        }
    }

    private func decodeCertificate(_ data: NSData, label: String) throws -> SecCertificate {
        guard let cert = SecCertificateCreateWithData(nil, data as CFData) else {
            throw PKIXBridgeError.invalidCertificate(reason: "Failed to decode DER for \(label)").asNSError()
        }
        return cert
    }

    private func buildPolicies() -> [SecPolicy] {
        var policies: [SecPolicy] = [SecPolicyCreateBasicX509()]
        if configuration.isRevocationEnabled {
            let flags = kSecRevocationOCSPMethod | kSecRevocationRequirePositiveResponse
            if let revocation = SecPolicyCreateRevocation(flags) {
                policies.append(revocation)
            }
        }
        return policies
    }

    private static func matchedAnchor(in trust: SecTrust) -> SecCertificate? {
        if #available(iOS 15.0, macOS 12.0, *) {
            guard let chain = SecTrustCopyCertificateChain(trust) as? [SecCertificate] else { return nil }
            return chain.last
        } else {
            return matchedAnchorLegacy(in: trust)
        }
    }

    @available(iOS, introduced: 13.0, deprecated: 15.0)
    @available(macOS, introduced: 11.0, deprecated: 12.0)
    private static func matchedAnchorLegacy(in trust: SecTrust) -> SecCertificate? {
        let count = SecTrustGetCertificateCount(trust)
        guard count > 0 else { return nil }
        return SecTrustGetCertificateAtIndex(trust, count - 1)
    }

    private func osStatusError(_ status: OSStatus) -> Error {
        let message = SecCopyErrorMessageString(status, nil) as String? ?? "OSStatus \(status)"
        return NSError(
            domain: NSOSStatusErrorDomain,
            code: Int(status),
            userInfo: [NSLocalizedDescriptionKey: message]
        )
    }
}
