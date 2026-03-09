import { ideCollaborationAtom } from './atoms';

describe('ideCollaborationAtom', () => {
    test('is exported and defined', () => {
        expect(ideCollaborationAtom).toBeDefined();
        expect(typeof ideCollaborationAtom).toBe('object');
    });
});
