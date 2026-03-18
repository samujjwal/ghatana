import RBush from 'rbush';

export interface SpatialItem {
    id: string;
    minX: number;
    minY: number;
    maxX: number;
    maxY: number;
}

class CanvasSpatialTree extends RBush<SpatialItem> {
    private itemMap = new Map<string, SpatialItem>();

    clearAndLoad(items: SpatialItem[]) {
        this.clear();
        this.itemMap.clear();
        for (const item of items) {
            this.itemMap.set(item.id, item);
        }
        this.load(items);
    }

    insertItem(item: SpatialItem) {
        // Remove existing item with same id first
        this.removeItem(item.id);
        this.itemMap.set(item.id, item);
        this.insert(item);
    }

    removeItem(id: string) {
        const existing = this.itemMap.get(id);
        if (existing) {
            this.remove(existing, (a, b) => a.id === b.id);
            this.itemMap.delete(id);
        }
    }

    findCollisions(item: SpatialItem, radius: number = 0): SpatialItem[] {
        return this.search({
            minX: item.minX - radius,
            minY: item.minY - radius,
            maxX: item.maxX + radius,
            maxY: item.maxY + radius,
        }).filter((result) => result.id !== item.id);
    }
}

const tree = new CanvasSpatialTree();

self.onmessage = (event: MessageEvent) => {
    const { type, payload, msgId } = event.data;
    
    try {
        if (type === 'BUILD') {
            tree.clearAndLoad(payload);
            self.postMessage({ type: 'BUILD_SUCCESS', msgId });
        } else if (type === 'SEARCH') {
            const { item, radius } = payload;
            const results = tree.findCollisions(item, radius);
            self.postMessage({ type: 'SEARCH_SUCCESS', msgId, payload: results });
        } else if (type === 'INSERT') {
            tree.insertItem(payload);
            self.postMessage({ type: 'INSERT_SUCCESS', msgId });
        } else if (type === 'REMOVE') {
            tree.removeItem(payload);
            self.postMessage({ type: 'REMOVE_SUCCESS', msgId });
        }
    } catch (e) {
        self.postMessage({ type: 'ERROR', msgId, payload: String(e) });
    }
};