#include <jni.h>
#include <android/log.h>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <vector>

#define LOG_TAG "ErasureCodingJni"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

constexpr uint16_t GF_POLY = 0x11d;

uint8_t gf_log_tab[256];
uint8_t gf_exp_tab[512];
std::once_flag gf_init_flag;

void gf_init_tables() {
    std::call_once(gf_init_flag, []() {
        uint16_t x = 1;
        for (int i = 0; i < 255; ++i) {
            gf_exp_tab[i] = static_cast<uint8_t>(x);
            gf_log_tab[x] = static_cast<uint8_t>(i);
            x <<= 1;
            if (x & 0x100) x ^= GF_POLY;
        }
        for (int i = 255; i < 512; ++i) {
            gf_exp_tab[i] = gf_exp_tab[i - 255];
        }
    });
}

inline uint8_t gf_mul(uint8_t a, uint8_t b) {
    if (a == 0 || b == 0) return 0;
    return gf_exp_tab[gf_log_tab[a] + gf_log_tab[b]];
}

inline uint8_t gf_inv(uint8_t a) {
    // a must be non-zero
    return gf_exp_tab[255 - gf_log_tab[a]];
}

inline uint8_t gf_pow(uint8_t base, int exponent) {
    if (base == 0) return 0;
    int log_base = gf_log_tab[base];
    int product = (log_base * exponent) % 255;
    if (product < 0) product += 255;
    return gf_exp_tab[product];
}

// Gauss-Jordan inversion of a k×k matrix in GF(256). Matrix is row-major.
// Returns true on success, false if singular. On success, mat is replaced by its inverse.
bool gf_invert_matrix(uint8_t* mat, int k) {
    std::vector<uint8_t> inv(static_cast<size_t>(k) * k, 0);
    for (int i = 0; i < k; ++i) inv[i * k + i] = 1;

    for (int col = 0; col < k; ++col) {
        int pivot = -1;
        for (int row = col; row < k; ++row) {
            if (mat[row * k + col] != 0) { pivot = row; break; }
        }
        if (pivot < 0) return false;
        if (pivot != col) {
            for (int j = 0; j < k; ++j) {
                std::swap(mat[col * k + j], mat[pivot * k + j]);
                std::swap(inv[col * k + j], inv[pivot * k + j]);
            }
        }
        uint8_t pv_inv = gf_inv(mat[col * k + col]);
        for (int j = 0; j < k; ++j) {
            mat[col * k + j] = gf_mul(mat[col * k + j], pv_inv);
            inv[col * k + j] = gf_mul(inv[col * k + j], pv_inv);
        }
        for (int row = 0; row < k; ++row) {
            if (row == col) continue;
            uint8_t factor = mat[row * k + col];
            if (factor == 0) continue;
            for (int j = 0; j < k; ++j) {
                mat[row * k + j] ^= gf_mul(factor, mat[col * k + j]);
                inv[row * k + j] ^= gf_mul(factor, inv[col * k + j]);
            }
        }
    }
    std::memcpy(mat, inv.data(), static_cast<size_t>(k) * k);
    return true;
}

// Build the N×K parity-generator matrix of the systematic Reed-Solomon code.
// Start from a (K+N)×K Vandermonde matrix V[i][j] = gf_pow(i+1, j), then multiply
// by the inverse of its top K×K block so that the top becomes identity. The bottom
// N×K block is the parity generator P (returned via out_P).
bool build_parity_matrix(int k, int n, std::vector<uint8_t>& out_P) {
    const int rows = k + n;
    std::vector<uint8_t> V(static_cast<size_t>(rows) * k);
    for (int i = 0; i < rows; ++i) {
        for (int j = 0; j < k; ++j) {
            V[i * k + j] = gf_pow(static_cast<uint8_t>(i + 1), j);
        }
    }
    std::vector<uint8_t> Vtop_inv(static_cast<size_t>(k) * k);
    std::memcpy(Vtop_inv.data(), V.data(), static_cast<size_t>(k) * k);
    if (!gf_invert_matrix(Vtop_inv.data(), k)) return false;

    out_P.assign(static_cast<size_t>(n) * k, 0);
    for (int i = 0; i < n; ++i) {
        for (int j = 0; j < k; ++j) {
            uint8_t sum = 0;
            for (int l = 0; l < k; ++l) {
                sum ^= gf_mul(V[(k + i) * k + l], Vtop_inv[l * k + j]);
            }
            out_P[i * k + j] = sum;
        }
    }
    return true;
}

// XOR into dst the result of multiplying src (length blockSize) by the GF(256) scalar coeff.
inline void gf_xor_scaled(uint8_t* dst, const uint8_t* src, int block_size, uint8_t coeff) {
    if (coeff == 0) return;
    if (coeff == 1) {
        for (int c = 0; c < block_size; ++c) dst[c] ^= src[c];
        return;
    }
    int log_coeff = gf_log_tab[coeff];
    for (int c = 0; c < block_size; ++c) {
        uint8_t s = src[c];
        if (s != 0) {
            dst[c] ^= gf_exp_tab[gf_log_tab[s] + log_coeff];
        }
    }
}

void throw_illegal_argument(JNIEnv* env, const char* msg) {
    jclass cls = env->FindClass("java/lang/IllegalArgumentException");
    if (cls != nullptr) env->ThrowNew(cls, msg);
}

void throw_illegal_state(JNIEnv* env, const char* msg) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls != nullptr) env->ThrowNew(cls, msg);
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_com_mobicloud_core_erasure_ErasureCodingJni_nativeEncode(
        JNIEnv* env, jobject /*thiz*/,
        jobject dataBuffer, jobject parityBuffer,
        jint k, jint n, jint blockSize) {
    gf_init_tables();
    if (k < 1 || n < 1 || k + n > 255 || blockSize <= 0) {
        throw_illegal_argument(env, "Invalid k/n/blockSize parameters");
        return;
    }
    auto* data = static_cast<uint8_t*>(env->GetDirectBufferAddress(dataBuffer));
    auto* parity = static_cast<uint8_t*>(env->GetDirectBufferAddress(parityBuffer));
    if (data == nullptr || parity == nullptr) {
        throw_illegal_argument(env, "Buffers must be direct");
        return;
    }
    jlong data_cap = env->GetDirectBufferCapacity(dataBuffer);
    jlong parity_cap = env->GetDirectBufferCapacity(parityBuffer);
    if (data_cap < static_cast<jlong>(k) * blockSize ||
        parity_cap < static_cast<jlong>(n) * blockSize) {
        throw_illegal_argument(env, "Buffer capacities too small");
        return;
    }

    std::vector<uint8_t> P;
    if (!build_parity_matrix(k, n, P)) {
        throw_illegal_state(env, "Failed to build parity matrix (singular Vandermonde)");
        return;
    }

    for (int i = 0; i < n; ++i) {
        uint8_t* p_row = parity + static_cast<size_t>(i) * blockSize;
        std::memset(p_row, 0, blockSize);
        for (int j = 0; j < k; ++j) {
            uint8_t coeff = P[i * k + j];
            gf_xor_scaled(p_row, data + static_cast<size_t>(j) * blockSize, blockSize, coeff);
        }
    }
}

extern "C" JNIEXPORT void JNICALL
Java_com_mobicloud_core_erasure_ErasureCodingJni_nativeDecode(
        JNIEnv* env, jobject /*thiz*/,
        jobject survivorsBuffer, jobject survivorIndicesBuffer, jobject outputBuffer,
        jint k, jint n, jint blockSize) {
    gf_init_tables();
    if (k < 1 || n < 1 || k + n > 255 || blockSize <= 0) {
        throw_illegal_argument(env, "Invalid k/n/blockSize parameters");
        return;
    }
    auto* survivors = static_cast<uint8_t*>(env->GetDirectBufferAddress(survivorsBuffer));
    auto* indices_raw = static_cast<uint8_t*>(env->GetDirectBufferAddress(survivorIndicesBuffer));
    auto* output = static_cast<uint8_t*>(env->GetDirectBufferAddress(outputBuffer));
    if (survivors == nullptr || indices_raw == nullptr || output == nullptr) {
        throw_illegal_argument(env, "Buffers must be direct");
        return;
    }
    jlong surv_cap = env->GetDirectBufferCapacity(survivorsBuffer);
    jlong idx_cap = env->GetDirectBufferCapacity(survivorIndicesBuffer);
    jlong out_cap = env->GetDirectBufferCapacity(outputBuffer);
    if (surv_cap < static_cast<jlong>(k) * blockSize ||
        out_cap < static_cast<jlong>(k) * blockSize ||
        idx_cap < static_cast<jlong>(k) * 4) {
        throw_illegal_argument(env, "Buffer capacities too small");
        return;
    }

    // Indices are stored as little-endian int32 (caller sets ByteOrder.LITTLE_ENDIAN).
    auto read_le_int32 = [](const uint8_t* p) -> int32_t {
        return static_cast<int32_t>(
                static_cast<uint32_t>(p[0]) |
                (static_cast<uint32_t>(p[1]) << 8) |
                (static_cast<uint32_t>(p[2]) << 16) |
                (static_cast<uint32_t>(p[3]) << 24));
    };

    std::vector<uint8_t> P;
    if (!build_parity_matrix(k, n, P)) {
        throw_illegal_state(env, "Failed to build parity matrix");
        return;
    }

    // Build the K×K decoding matrix: for each survivor r, copy row `indices[r]` of the
    // systematic (K+N)×K matrix. Top K rows are identity, bottom N rows are P.
    std::vector<uint8_t> M(static_cast<size_t>(k) * k, 0);
    for (int r = 0; r < k; ++r) {
        int32_t idx = read_le_int32(indices_raw + r * 4);
        if (idx < 0 || idx >= k + n) {
            throw_illegal_argument(env, "Survivor index out of range");
            return;
        }
        if (idx < k) {
            M[r * k + idx] = 1;
        } else {
            std::memcpy(&M[r * k], &P[(idx - k) * k], k);
        }
    }

    if (!gf_invert_matrix(M.data(), k)) {
        throw_illegal_state(env, "Cannot decode: survivor matrix is singular");
        return;
    }

    for (int i = 0; i < k; ++i) {
        uint8_t* out_row = output + static_cast<size_t>(i) * blockSize;
        std::memset(out_row, 0, blockSize);
        for (int j = 0; j < k; ++j) {
            uint8_t coeff = M[i * k + j];
            gf_xor_scaled(out_row, survivors + static_cast<size_t>(j) * blockSize,
                          blockSize, coeff);
        }
    }
}
