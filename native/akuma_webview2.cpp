#include <windows.h>
#include <wrl.h>
#include <WebView2.h>
#include <jni.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <condition_variable>
#include <filesystem>
#include <fstream>
#include <iomanip>
#include <mutex>
#include <sstream>
#include <string>
#include <thread>

using Microsoft::WRL::Callback;
using Microsoft::WRL::ComPtr;

namespace {
constexpr UINT WM_AKUMA_UPDATE = WM_APP + 101;
constexpr UINT WM_AKUMA_ZOOM = WM_APP + 102;
constexpr UINT WM_AKUMA_CLOSE = WM_APP + 103;

std::thread g_thread;
DWORD g_thread_id = 0;
HWND g_parent = nullptr;
std::wstring g_initial_url;

ComPtr<ICoreWebView2Environment> g_environment;
ComPtr<ICoreWebView2Controller> g_controller;
ComPtr<ICoreWebView2Controller2> g_controller2;
ComPtr<ICoreWebView2> g_webview;

std::atomic<bool> g_ready{false};
std::atomic<bool> g_running{false};
std::atomic<int> g_x{0};
std::atomic<int> g_y{0};
std::atomic<int> g_w{1};
std::atomic<int> g_h{1};
std::atomic<int> g_logical_w{1};
std::atomic<int> g_logical_h{1};
std::atomic<double> g_zoom{1.0};
std::atomic<bool> g_visible{true};

std::mutex g_state_mutex;
std::condition_variable g_thread_cv;
bool g_thread_started = false;
std::string g_last_error;
std::string g_current_url;
std::string g_last_event;
double g_current_zoom = 1.0;

std::filesystem::path log_path() {
    return std::filesystem::current_path() / "logs" / "akumavote-webview2.log";
}

std::string hex_hr(HRESULT hr) {
    std::ostringstream out;
    out << "0x" << std::uppercase << std::hex << static_cast<unsigned long>(hr);
    return out.str();
}

std::string wide_to_utf8(const wchar_t* text) {
    if (!text) {
        return {};
    }
    int size = WideCharToMultiByte(CP_UTF8, 0, text, -1, nullptr, 0, nullptr, nullptr);
    if (size <= 1) {
        return {};
    }
    std::string result(static_cast<size_t>(size - 1), '\0');
    WideCharToMultiByte(CP_UTF8, 0, text, -1, result.data(), size, nullptr, nullptr);
    return result;
}

std::wstring utf8_to_wide(const std::string& text) {
    if (text.empty()) {
        return {};
    }
    int size = MultiByteToWideChar(CP_UTF8, 0, text.data(), static_cast<int>(text.size()), nullptr, 0);
    if (size <= 0) {
        return {};
    }
    std::wstring result(static_cast<size_t>(size), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, text.data(), static_cast<int>(text.size()), result.data(), size);
    return result;
}

void native_log(const std::string& message) {
    try {
        std::filesystem::create_directories(log_path().parent_path());
        std::ofstream out(log_path(), std::ios::app);
        if (!out) {
            return;
        }
        SYSTEMTIME now{};
        GetLocalTime(&now);
        out << std::setfill('0')
            << now.wYear << '-'
            << std::setw(2) << now.wMonth << '-'
            << std::setw(2) << now.wDay << ' '
            << std::setw(2) << now.wHour << ':'
            << std::setw(2) << now.wMinute << ':'
            << std::setw(2) << now.wSecond << '.'
            << std::setw(3) << now.wMilliseconds
            << " [WebView2] " << message << '\n';
    } catch (...) {
    }
}

void set_error(const std::string& error) {
    std::lock_guard lock(g_state_mutex);
    g_last_error = error;
    g_last_event = error;
}

void set_event(const std::string& event) {
    std::lock_guard lock(g_state_mutex);
    g_last_event = event;
}

void update_url(const std::string& url) {
    std::lock_guard lock(g_state_mutex);
    g_current_url = url;
}

void apply_bounds() {
    if (!g_controller || !g_parent) {
        return;
    }
    RECT client{};
    GetClientRect(g_parent, &client);
    int logical_w = std::max(1, g_logical_w.load());
    int logical_h = std::max(1, g_logical_h.load());
    double sx = static_cast<double>(client.right - client.left) / logical_w;
    double sy = static_cast<double>(client.bottom - client.top) / logical_h;
    if (sx <= 0.0) sx = 1.0;
    if (sy <= 0.0) sy = 1.0;

    RECT bounds{};
    bounds.left = static_cast<LONG>(std::lround(g_x.load() * sx));
    bounds.top = static_cast<LONG>(std::lround(g_y.load() * sy));
    bounds.right = static_cast<LONG>(std::lround((g_x.load() + g_w.load()) * sx));
    bounds.bottom = static_cast<LONG>(std::lround((g_y.load() + g_h.load()) * sy));

    g_controller->put_Bounds(bounds);
    g_controller->put_IsVisible(g_visible.load() ? TRUE : FALSE);
}

void apply_zoom() {
    double zoom = g_zoom.load();
    if (g_controller2) {
        HRESULT hr = g_controller2->put_ZoomFactor(zoom);
        if (FAILED(hr)) {
            set_error("put_ZoomFactor failed: " + hex_hr(hr));
            native_log("put_ZoomFactor failed: " + hex_hr(hr));
            return;
        }
        g_current_zoom = zoom;
    }
}

void install_webview_events() {
    if (!g_webview) {
        return;
    }

    EventRegistrationToken token{};

    g_webview->add_NavigationStarting(
        Callback<ICoreWebView2NavigationStartingEventHandler>(
            [](ICoreWebView2*, ICoreWebView2NavigationStartingEventArgs* args) -> HRESULT {
                LPWSTR uri = nullptr;
                args->get_Uri(&uri);
                std::string url = wide_to_utf8(uri);
                if (uri) {
                    CoTaskMemFree(uri);
                }
                update_url(url);
                set_event("NavigationStarting url=" + url);
                native_log("NavigationStarting url=" + url);
                return S_OK;
            }).Get(),
        &token);

    g_webview->add_NavigationCompleted(
        Callback<ICoreWebView2NavigationCompletedEventHandler>(
            [](ICoreWebView2*, ICoreWebView2NavigationCompletedEventArgs* args) -> HRESULT {
                BOOL success = FALSE;
                COREWEBVIEW2_WEB_ERROR_STATUS status = COREWEBVIEW2_WEB_ERROR_STATUS_UNKNOWN;
                args->get_IsSuccess(&success);
                args->get_WebErrorStatus(&status);
                std::ostringstream event;
                event << "NavigationCompleted success=" << (success ? "true" : "false")
                      << " webErrorStatus=" << static_cast<int>(status);
                set_event(event.str());
                native_log(event.str());
                return S_OK;
            }).Get(),
        &token);

    g_webview->add_SourceChanged(
        Callback<ICoreWebView2SourceChangedEventHandler>(
            [](ICoreWebView2*, ICoreWebView2SourceChangedEventArgs*) -> HRESULT {
                LPWSTR uri = nullptr;
                g_webview->get_Source(&uri);
                std::string url = wide_to_utf8(uri);
                if (uri) {
                    CoTaskMemFree(uri);
                }
                update_url(url);
                set_event("SourceChanged url=" + url);
                native_log("SourceChanged url=" + url);
                return S_OK;
            }).Get(),
        &token);

    g_webview->add_DocumentTitleChanged(
        Callback<ICoreWebView2DocumentTitleChangedEventHandler>(
            [](ICoreWebView2* sender, IUnknown*) -> HRESULT {
                LPWSTR title = nullptr;
                sender->get_DocumentTitle(&title);
                std::string value = wide_to_utf8(title);
                if (title) {
                    CoTaskMemFree(title);
                }
                set_event("DocumentTitleChanged title=" + value);
                native_log("DocumentTitleChanged title=" + value);
                return S_OK;
            }).Get(),
        &token);

    g_webview->add_NewWindowRequested(
        Callback<ICoreWebView2NewWindowRequestedEventHandler>(
            [](ICoreWebView2* sender, ICoreWebView2NewWindowRequestedEventArgs* args) -> HRESULT {
                LPWSTR uri = nullptr;
                args->get_Uri(&uri);
                std::string url = wide_to_utf8(uri);
                if (uri) {
                    CoTaskMemFree(uri);
                }
                args->put_Handled(TRUE);
                if (!url.empty()) {
                    sender->Navigate(utf8_to_wide(url).c_str());
                }
                set_event("NewWindowRequested handled url=" + url);
                native_log("NewWindowRequested handled url=" + url);
                return S_OK;
            }).Get(),
        &token);
}

void create_environment() {
    auto user_data = (std::filesystem::current_path() / "config" / "akumavote" / "webview2").wstring();
    HRESULT hr = CreateCoreWebView2EnvironmentWithOptions(
        nullptr,
        user_data.c_str(),
        nullptr,
        Callback<ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler>(
            [](HRESULT result, ICoreWebView2Environment* env) -> HRESULT {
                if (FAILED(result) || !env) {
                    set_error("CreateCoreWebView2Environment failed: " + hex_hr(result));
                    native_log("CreateCoreWebView2Environment failed: " + hex_hr(result));
                    return S_OK;
                }

                g_environment = env;
                HRESULT controller_hr = env->CreateCoreWebView2Controller(
                    g_parent,
                    Callback<ICoreWebView2CreateCoreWebView2ControllerCompletedHandler>(
                        [](HRESULT result, ICoreWebView2Controller* controller) -> HRESULT {
                            if (FAILED(result) || !controller) {
                                set_error("CreateCoreWebView2Controller failed: " + hex_hr(result));
                                native_log("CreateCoreWebView2Controller failed: " + hex_hr(result));
                                return S_OK;
                            }

                            g_controller = controller;
                            g_controller.As(&g_controller2);

                            if (FAILED(g_controller->get_CoreWebView2(&g_webview)) || !g_webview) {
                                set_error("get_CoreWebView2 failed.");
                                native_log("get_CoreWebView2 failed.");
                                g_controller.Reset();
                                return S_OK;
                            }

                            install_webview_events();
                            apply_bounds();
                            apply_zoom();

                            HRESULT nav_hr = g_webview->Navigate(g_initial_url.c_str());
                            if (FAILED(nav_hr)) {
                                set_error("Navigate failed: " + hex_hr(nav_hr));
                                native_log("Navigate failed: " + hex_hr(nav_hr));
                                return S_OK;
                            }

                            {
                                std::lock_guard lock(g_state_mutex);
                                g_ready.store(true);
                                g_current_url = wide_to_utf8(g_initial_url.c_str());
                                g_last_event = "ControllerReady";
                                g_last_error.clear();
                            }
                            native_log("WebView2 controller ready, url=" + wide_to_utf8(g_initial_url.c_str()));
                            return S_OK;
                        }).Get());
                if (FAILED(controller_hr)) {
                    set_error("CreateCoreWebView2Controller call failed: " + hex_hr(controller_hr));
                    native_log("CreateCoreWebView2Controller call failed: " + hex_hr(controller_hr));
                }
                return S_OK;
            }).Get());

    if (FAILED(hr)) {
        set_error("CreateCoreWebView2Environment call failed: " + hex_hr(hr));
        native_log("CreateCoreWebView2Environment call failed: " + hex_hr(hr));
    }
}

void cleanup() {
    g_ready.store(false);
    if (g_controller) {
        g_controller->put_IsVisible(FALSE);
    }
    g_webview.Reset();
    g_controller2.Reset();
    if (g_controller) {
        g_controller->Close();
    }
    g_controller.Reset();
    g_environment.Reset();
    g_parent = nullptr;
}

void browser_thread_main() {
    HRESULT com_hr = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
    if (FAILED(com_hr)) {
        set_error("CoInitializeEx failed: " + hex_hr(com_hr));
        native_log("CoInitializeEx failed: " + hex_hr(com_hr));
        return;
    }

    MSG message{};
    PeekMessageW(&message, nullptr, WM_USER, WM_USER, PM_NOREMOVE);

    {
        std::lock_guard lock(g_state_mutex);
        g_thread_id = GetCurrentThreadId();
        g_thread_started = true;
    }
    g_thread_cv.notify_all();

    create_environment();

    while (GetMessageW(&message, nullptr, 0, 0) > 0) {
        switch (message.message) {
            case WM_AKUMA_UPDATE:
                apply_bounds();
                break;
            case WM_AKUMA_ZOOM:
                apply_zoom();
                break;
            case WM_AKUMA_CLOSE:
                cleanup();
                PostQuitMessage(0);
                break;
            default:
                break;
        }
    }

    cleanup();
    CoUninitialize();

    {
        std::lock_guard lock(g_state_mutex);
        g_thread_id = 0;
        g_thread_started = false;
    }
}

bool post(UINT message) {
    DWORD thread_id;
    {
        std::lock_guard lock(g_state_mutex);
        thread_id = g_thread_id;
    }
    if (!thread_id) {
        return false;
    }
    return PostThreadMessageW(thread_id, message, 0, 0) != FALSE;
}

std::string state_string() {
    std::lock_guard lock(g_state_mutex);
    std::ostringstream state;
    state << "ready=" << (g_ready.load() ? "true" : "false")
          << ", running=" << (g_running.load() ? "true" : "false")
          << ", url=" << (g_current_url.empty() ? "" : g_current_url)
          << ", zoom=" << std::fixed << std::setprecision(3) << g_current_zoom
          << ", event=" << (g_last_event.empty() ? "" : g_last_event)
          << ", error=" << (g_last_error.empty() ? "" : g_last_error);
    return state.str();
}
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_akumavote_WebView2Native_nativeIsAvailable(JNIEnv*, jclass) {
    LPWSTR version = nullptr;
    HRESULT hr = GetAvailableCoreWebView2BrowserVersionString(nullptr, &version);
    if (SUCCEEDED(hr) && version) {
        std::string value = wide_to_utf8(version);
        CoTaskMemFree(version);
        native_log("WebView2 Runtime available: " + value);
        return JNI_TRUE;
    }
    native_log("WebView2 Runtime unavailable: " + hex_hr(hr));
    set_error("WebView2 Runtime unavailable: " + hex_hr(hr));
    if (version) {
        CoTaskMemFree(version);
    }
    return JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_akumavote_WebView2Native_nativeCreate(
    JNIEnv* env, jclass, jlong parent_hwnd, jstring url,
    jint logical_window_width, jint logical_window_height,
    jint x, jint y, jint width, jint height, jdouble zoom_factor) {

    if (g_running.load()) {
        return JNI_FALSE;
    }

    const jchar* chars = env->GetStringChars(url, nullptr);
    if (!chars) {
        set_error("Cannot read URL from Java.");
        return JNI_FALSE;
    }
    std::wstring initial(reinterpret_cast<const wchar_t*>(chars),
                         static_cast<size_t>(env->GetStringLength(url)));
    env->ReleaseStringChars(url, chars);

    g_parent = reinterpret_cast<HWND>(static_cast<uintptr_t>(parent_hwnd));
    g_initial_url = initial;
    g_logical_w.store(std::max(1, static_cast<int>(logical_window_width)));
    g_logical_h.store(std::max(1, static_cast<int>(logical_window_height)));
    g_x.store(x);
    g_y.store(y);
    g_w.store(std::max(1, static_cast<int>(width)));
    g_h.store(std::max(1, static_cast<int>(height)));
    g_zoom.store(zoom_factor);
    g_visible.store(true);

    {
        std::lock_guard lock(g_state_mutex);
        g_last_error.clear();
        g_last_event = "Starting";
        g_current_url = wide_to_utf8(initial.c_str());
        g_current_zoom = zoom_factor;
        g_thread_started = false;
    }

    g_running.store(true);
    g_thread = std::thread(browser_thread_main);

    {
        std::unique_lock lock(g_state_mutex);
        g_thread_cv.wait_for(lock, std::chrono::seconds(2), [] { return g_thread_started; });
    }

    native_log("Create requested: url=" + wide_to_utf8(initial.c_str()));
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_dev_akumavote_WebView2Native_nativeSetBounds(
    JNIEnv*, jclass, jlong parent_hwnd,
    jint logical_window_width, jint logical_window_height,
    jint x, jint y, jint width, jint height) {

    g_parent = reinterpret_cast<HWND>(static_cast<uintptr_t>(parent_hwnd));
    g_logical_w.store(std::max(1, static_cast<int>(logical_window_width)));
    g_logical_h.store(std::max(1, static_cast<int>(logical_window_height)));
    g_x.store(x);
    g_y.store(y);
    g_w.store(std::max(1, static_cast<int>(width)));
    g_h.store(std::max(1, static_cast<int>(height)));
    post(WM_AKUMA_UPDATE);
}

extern "C" JNIEXPORT void JNICALL
Java_dev_akumavote_WebView2Native_nativeSetZoomFactor(
    JNIEnv*, jclass, jdouble zoom_factor) {
    g_zoom.store(zoom_factor);
    {
        std::lock_guard lock(g_state_mutex);
        g_current_zoom = zoom_factor;
    }
    post(WM_AKUMA_ZOOM);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_dev_akumavote_WebView2Native_nativeIsReady(JNIEnv*, jclass) {
    return g_ready.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_dev_akumavote_WebView2Native_nativeGetState(JNIEnv* env, jclass) {
    std::string state = state_string();
    return env->NewStringUTF(state.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_dev_akumavote_WebView2Native_nativeClose(JNIEnv*, jclass) {
    if (!g_running.load()) {
        return;
    }
    post(WM_AKUMA_CLOSE);
    if (g_thread.joinable()) {
        g_thread.join();
    }
    g_running.store(false);
    native_log("Closed.");
}
