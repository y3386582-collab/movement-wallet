(function () {
  "use strict";

  const _pending = new Map();
  let _callId = 0;

  function callNative(method, params = {}) {
    return new Promise((resolve, reject) => {
      const id = ++_callId;
      _pending.set(id, { resolve, reject });
      const payload = JSON.stringify({ id, method, params });

      if (window.MovementBridge && window.MovementBridge.postMessage) {
        window.MovementBridge.postMessage(payload);
        return;
      }
      _mockHandler(id, method);
    });
  }

  window.movementCallback = function (jsonStr) {
    try {
      const { id, result, error } = JSON.parse(jsonStr);
      const pending = _pending.get(id);
      if (!pending) return;
      _pending.delete(id);
      if (error) pending.reject(new Error(error));
      else pending.resolve(result);
    } catch (e) {
      console.error("[MovementWallet] callback error", e);
    }
  };

  function _mockHandler(id, method) {
    setTimeout(() => {
      const cb = window.movementCallback;
      switch (method) {
        case "connect":
          cb(JSON.stringify({ id, result: {
            account: {
              address: "0x0000000000000000000000000000000000000001",
              publicKey: "0xMOCKPUBKEY"
            },
            status: "Approved"
          }}));
          break;
        case "disconnect":
          cb(JSON.stringify({ id, result: {} }));
          break;
        case "account":
          cb(JSON.stringify({ id, result: {
            address: "0x0000000000000000000000000000000000000001",
            publicKey: "0xMOCKPUBKEY"
          }}));
          break;
        case "network":
          cb(JSON.stringify({ id, result: {
            name: "Movement Mainnet",
            chainId: "177",
            url: "https://mainnet.movementnetwork.xyz/v1"
          }}));
          break;
        case "signTransaction":
          cb(JSON.stringify({ id, result: {
            signature: "0xMOCKSIG"
          }}));
          break;
        case "signAndSubmitTransaction":
          cb(JSON.stringify({ id, result: {
            hash: "0xMOCKHASH"
          }}));
          break;
        case "signMessage":
          cb(JSON.stringify({ id, result: {
            signature: "0xMOCKMSGSIG"
          }}));
          break;
        default:
          cb(JSON.stringify({ id, error: "Unknown: " + method }));
      }
    }, 60);
  }

  let _connectedAccount = null;
  const _accountListeners = new Set();
  const _networkListeners = new Set();

  const MovementWallet = Object.freeze({
    version: "1.0.0",
    name: "Movement Wallet",
    icon: "data:image/svg+xml;base64,PHN2ZyB3aWR0aD0iMTI4IiBoZWlnaHQ9IjEyOCIgdmlld0JveD0iMCAwIDEyOCAxMjgiIGZpbGw9Im5vbmUiIHhtbG5zPSJodHRwOi8vd3d3LnczLm9yZy8yMDAwL3N2ZyI+PHJlY3Qgd2lkdGg9IjEyOCIgaGVpZ2h0PSIxMjgiIHJ4PSIyNCIgZmlsbD0iIzFBMUEyRSIvPjxwYXRoIGQ9Ik0yNCA5Nkw2NCAzMkwxMDQgOTZINzhMNjQgNzBMNTAgOTZIMjRaIiBmaWxsPSIjMDBGNUZGIi8+PC9zdmc+",
    chains: ["aptos:mainnet", "aptos:testnet", "movement:mainnet"],

    get features() {
      return {
        "aptos:connect": {
          version: "1.0.0",
          connect: async (input) => {
            const result = await callNative("connect",
              { silent: input?.silent ?? false });
            _connectedAccount = result.account;
            return result;
          }
        },
        "aptos:disconnect": {
          version: "1.0.0",
          disconnect: async () => {
            await callNative("disconnect");
            _connectedAccount = null;
          }
        },
        "aptos:account": {
          version: "1.0.0",
          account: async () => {
            const acct = await callNative("account");
            _connectedAccount = acct;
            return acct;
          }
        },
        "aptos:network": {
          version: "1.0.0",
          network: async () => await callNative("network")
        },
        "aptos:signTransaction": {
          version: "1.1.0",
          signTransaction: async (tx, asFeePayer) =>
            await callNative("signTransaction",
              { transaction: tx, asFeePayer: !!asFeePayer })
        },
        "aptos:signAndSubmitTransaction": {
          version: "1.1.0",
          signAndSubmitTransaction: async (input) =>
            await callNative("signAndSubmitTransaction", { input })
        },
        "aptos:signMessage": {
          version: "1.0.0",
          signMessage: async (input) =>
            await callNative("signMessage", { input })
        },
        "aptos:onAccountChange": {
          version: "1.0.0",
          onAccountChange: (cb) => {
            _accountListeners.add(cb);
            return () => _accountListeners.delete(cb);
          }
        },
        "aptos:onNetworkChange": {
          version: "1.0.0",
          onNetworkChange: (cb) => {
            _networkListeners.add(cb);
            return () => _networkListeners.delete(cb);
          }
        }
      };
    },

    get accounts() {
      if (!_connectedAccount) return [];
      return [{
        address: _connectedAccount.address,
        publicKey: _connectedAccount.publicKey
          ? [_connectedAccount.publicKey] : [],
        chains: ["aptos:mainnet", "movement:mainnet"],
        features: ["aptos:connect", "aptos:signTransaction"],
        label: "Movement Wallet"
      }];
    }
  });

  function registerWallet(wallet) {
    window.addEventListener(
      "wallet-standard:app-ready",
      ({ detail: register }) => {
        try { register(wallet); } catch (e) {}
      }
    );

    try {
      window.dispatchEvent(
        new CustomEvent("wallet-standard:register-wallet", {
          detail: (registerFn) => {
            try { registerFn(wallet); } catch (e) {}
          },
          bubbles: false,
          cancelable: false,
          composed: false,
        })
      );
    } catch (e) {}
  }

  registerWallet(MovementWallet);
  console.log("[MovementWallet] Wallet Standard adapter registered ✓");

})();
