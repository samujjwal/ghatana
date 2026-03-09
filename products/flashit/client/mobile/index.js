import { registerRootComponent } from 'expo';
import App from './App';

// Use different registration for web
if (typeof window !== 'undefined' && window.document) {
    // Web environment - use React DOM directly
    import('react-dom/client').then(({ createRoot }) => {
        const container = document.getElementById('root');
        if (container) {
            const root = createRoot(container);
            root.render(App);
        }
    });
} else {
    // Native environment - use Expo
    registerRootComponent(App);
}
