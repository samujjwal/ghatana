// Setup for @ghatana/ui-builder tests.
import { enableMapSet } from 'immer';
import { installMockStorage } from '@ghatana/platform-testing/vitest-browser';

enableMapSet();
installMockStorage(window, 'localStorage');
