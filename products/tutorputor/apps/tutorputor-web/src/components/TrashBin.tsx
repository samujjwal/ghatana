/**
 * Trash Bin / Recycle Bin Component
 *
 * UI for managing deleted content including:
 * - List of deleted items
 * - Restore action
 * - Permanent delete with confirmation
 * - Bulk operations
 * - Auto-cleanup countdown
 *
 * @doc.type component
 * @doc.purpose Allow users to restore or permanently delete trashed content
 * @doc.layer product
 * @doc.pattern Component
 */
import { useState } from "react";
import {
  Trash2,
  RotateCcw,
  AlertTriangle,
  Clock,
  FileText,
  BookOpen,
  Layers,
  CheckSquare,
  Loader2,
} from "lucide-react";
import {
  Button,
  Card,
  Badge,
  AlertDialog,
  Checkbox,
  EmptyState,
} from "@ghatana/design-system";

export interface TrashItem {
  id: string;
  itemId: string;
  itemType: "contentAsset" | "learningExperience" | "module" | "assessment";
  title: string;
  deletedAt: Date;
  deletedBy: string;
  expiresAt: Date;
}

interface TrashBinProps {
  items: TrashItem[];
  onRestore: (ids: string[]) => Promise<void>;
  onPermanentDelete: (ids: string[]) => Promise<void>;
  onEmptyTrash: () => Promise<void>;
  isLoading?: boolean;
}

const ITEM_TYPE_ICONS: Record<TrashItem["itemType"], typeof FileText> = {
  contentAsset: FileText,
  learningExperience: BookOpen,
  module: Layers,
  assessment: CheckSquare,
};

const ITEM_TYPE_LABELS: Record<TrashItem["itemType"], string> = {
  contentAsset: "Content",
  learningExperience: "Experience",
  module: "Module",
  assessment: "Assessment",
};

export function TrashBin({
  items,
  onRestore,
  onPermanentDelete,
  onEmptyTrash,
  isLoading = false,
}: TrashBinProps) {
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [showRestoreDialog, setShowRestoreDialog] = useState(false);
  const [showDeleteDialog, setShowDeleteDialog] = useState(false);
  const [showEmptyDialog, setShowEmptyDialog] = useState(false);
  const [actionInProgress, setActionInProgress] = useState(false);

  const toggleSelection = (id: string) => {
    setSelectedIds((prev) =>
      prev.includes(id) ? prev.filter((i) => i !== id) : [...prev, id],
    );
  };

  const toggleAll = () => {
    if (selectedIds.length === items.length) {
      setSelectedIds([]);
    } else {
      setSelectedIds(items.map((item) => item.id));
    }
  };

  const handleRestore = async () => {
    setActionInProgress(true);
    try {
      await onRestore(selectedIds);
      setSelectedIds([]);
      setShowRestoreDialog(false);
    } finally {
      setActionInProgress(false);
    }
  };

  const handleDelete = async () => {
    setActionInProgress(true);
    try {
      await onPermanentDelete(selectedIds);
      setSelectedIds([]);
      setShowDeleteDialog(false);
    } finally {
      setActionInProgress(false);
    }
  };

  const handleEmptyTrash = async () => {
    setActionInProgress(true);
    try {
      await onEmptyTrash();
      setShowEmptyDialog(false);
    } finally {
      setActionInProgress(false);
    }
  };

  const getDaysRemaining = (expiresAt: Date) => {
    const days = Math.ceil(
      (new Date(expiresAt).getTime() - Date.now()) / (1000 * 60 * 60 * 24),
    );
    return days;
  };

  const getUrgencyColor = (daysRemaining: number) => {
    if (daysRemaining <= 3) return "text-red-600";
    if (daysRemaining <= 7) return "text-yellow-600";
    return "text-gray-500";
  };

  if (items.length === 0) {
    return (
      <Card className="p-8">
        <EmptyState
          icon={<Trash2 className="w-12 h-12 text-gray-400" />}
          title="Trash is Empty"
          description="Deleted items will appear here for 30 days before being permanently removed."
        />
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      {/* Header Actions */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-4">
          <Checkbox
            checked={selectedIds.length === items.length && items.length > 0}
            onChange={toggleAll}
            id="select-all"
          />
          <label htmlFor="select-all" className="text-sm font-medium">
            {selectedIds.length > 0
              ? `${selectedIds.length} selected`
              : "Select all"}
          </label>
        </div>

        <div className="flex gap-2">
          {selectedIds.length > 0 && (
            <>
              <Button
                variant="outline"
                onClick={() => setShowRestoreDialog(true)}
                disabled={actionInProgress}
              >
                <RotateCcw className="w-4 h-4 mr-1" />
                Restore
              </Button>
              <Button
                variant="outline"
                className="text-red-600 hover:text-red-700"
                onClick={() => setShowDeleteDialog(true)}
                disabled={actionInProgress}
              >
                <Trash2 className="w-4 h-4 mr-1" />
                Delete Permanently
              </Button>
            </>
          )}
          <Button
            variant="ghost"
            className="text-red-600"
            onClick={() => setShowEmptyDialog(true)}
            disabled={actionInProgress}
          >
            Empty Trash
          </Button>
        </div>
      </div>

      {/* Items List */}
      <div className="space-y-2">
        {items.map((item) => {
          const Icon = ITEM_TYPE_ICONS[item.itemType];
          const daysRemaining = getDaysRemaining(item.expiresAt);

          return (
            <Card
              key={item.id}
              className={`p-4 transition-colors ${
                selectedIds.includes(item.id) ? "bg-blue-50 border-blue-300" : ""
              }`}
            >
              <div className="flex items-center gap-4">
                <Checkbox
                  checked={selectedIds.includes(item.id)}
                  onChange={() => toggleSelection(item.id)}
                />

                <div className="p-2 bg-gray-100 rounded-lg">
                  <Icon className="w-5 h-5 text-gray-600" />
                </div>

                <div className="flex-1 min-w-0">
                  <h3 className="font-medium text-gray-900 truncate">
                    {item.title}
                  </h3>
                  <div className="flex items-center gap-2 text-sm text-gray-500 mt-1">
                    <Badge variant="outline" className="text-xs">
                      {ITEM_TYPE_LABELS[item.itemType]}
                    </Badge>
                    <span>•</span>
                    <span>Deleted {new Date(item.deletedAt).toLocaleDateString()}</span>
                  </div>
                </div>

                <div className="flex items-center gap-2 text-sm">
                  <Clock className={`w-4 h-4 ${getUrgencyColor(daysRemaining)}`} />
                  <span className={getUrgencyColor(daysRemaining)}>
                    {daysRemaining} days left
                  </span>
                </div>
              </div>
            </Card>
          );
        })}
      </div>

      {/* Restore Confirmation Dialog */}
      <AlertDialog open={showRestoreDialog} onOpenChange={setShowRestoreDialog}>
        <AlertDialog.Content>
          <AlertDialog.Header>
            <AlertDialog.Title className="flex items-center gap-2">
              <RotateCcw className="w-5 h-5 text-blue-500" />
              Restore Items?
            </AlertDialog.Title>
            <AlertDialog.Description>
              Are you sure you want to restore {selectedIds.length}{" "}
              {selectedIds.length === 1 ? "item" : "items"}? They will be
              returned to their original location.
            </AlertDialog.Description>
          </AlertDialog.Header>
          <AlertDialog.Footer>
            <Button variant="outline" onClick={() => setShowRestoreDialog(false)}>
              Cancel
            </Button>
            <Button onClick={handleRestore} disabled={actionInProgress}>
              {actionInProgress ? (
                <Loader2 className="w-4 h-4 animate-spin mr-1" />
              ) : (
                <RotateCcw className="w-4 h-4 mr-1" />
              )}
              Restore
            </Button>
          </AlertDialog.Footer>
        </AlertDialog.Content>
      </AlertDialog>

      {/* Delete Confirmation Dialog */}
      <AlertDialog open={showDeleteDialog} onOpenChange={setShowDeleteDialog}>
        <AlertDialog.Content>
          <AlertDialog.Header>
            <AlertDialog.Title className="flex items-center gap-2 text-red-600">
              <AlertTriangle className="w-5 h-5" />
              Delete Permanently?
            </AlertDialog.Title>
            <AlertDialog.Description className="space-y-2">
              <p>
                Are you sure you want to permanently delete {selectedIds.length}{" "}
                {selectedIds.length === 1 ? "item" : "items"}?
              </p>
              <p className="text-red-600 font-medium">
                This action cannot be undone. The items will be permanently lost.
              </p>
            </AlertDialog.Description>
          </AlertDialog.Header>
          <AlertDialog.Footer>
            <Button variant="outline" onClick={() => setShowDeleteDialog(false)}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={handleDelete} disabled={actionInProgress}>
              {actionInProgress ? (
                <Loader2 className="w-4 h-4 animate-spin mr-1" />
              ) : (
                <Trash2 className="w-4 h-4 mr-1" />
              )}
              Delete Forever
            </Button>
          </AlertDialog.Footer>
        </AlertDialog.Content>
      </AlertDialog>

      {/* Empty Trash Dialog */}
      <AlertDialog open={showEmptyDialog} onOpenChange={setShowEmptyDialog}>
        <AlertDialog.Content>
          <AlertDialog.Header>
            <AlertDialog.Title className="flex items-center gap-2 text-red-600">
              <AlertTriangle className="w-5 h-5" />
              Empty Trash?
            </AlertDialog.Title>
            <AlertDialog.Description className="space-y-2">
              <p>
                Are you sure you want to empty the trash? This will permanently
                delete all {items.length} {items.length === 1 ? "item" : "items"}.
              </p>
              <p className="text-red-600 font-medium">
                This action cannot be undone.
              </p>
            </AlertDialog.Description>
          </AlertDialog.Header>
          <AlertDialog.Footer>
            <Button variant="outline" onClick={() => setShowEmptyDialog(false)}>
              Cancel
            </Button>
            <Button variant="destructive" onClick={handleEmptyTrash} disabled={actionInProgress}>
              {actionInProgress ? (
                <Loader2 className="w-4 h-4 animate-spin mr-1" />
              ) : (
                <Trash2 className="w-4 h-4 mr-1" />
              )}
              Empty Trash
            </Button>
          </AlertDialog.Footer>
        </AlertDialog.Content>
      </AlertDialog>
    </div>
  );
}

// Hook for trash operations
export function useTrashBin(tenantId: string) {
  const [items, setItems] = useState<TrashItem[]>([]);
  const [isLoading, setIsLoading] = useState(false);

  const fetchTrashItems = async () => {
    setIsLoading(true);
    try {
      const response = await fetch(`/api/trash?tenantId=${tenantId}`);
      const data = await response.json();
      setItems(data.items);
    } catch (error) {
      console.error("Failed to fetch trash items:", error);
    } finally {
      setIsLoading(false);
    }
  };

  const restoreItems = async (ids: string[]) => {
    try {
      const response = await fetch("/api/trash/restore", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ tenantId, trashItemIds: ids }),
      });

      if (!response.ok) throw new Error("Restore failed");

      // Remove restored items from list
      setItems((prev) => prev.filter((item) => !ids.includes(item.id)));
    } catch (error) {
      console.error("Failed to restore items:", error);
      throw error;
    }
  };

  const deleteItems = async (ids: string[]) => {
    try {
      const response = await fetch("/api/trash/delete", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ tenantId, trashItemIds: ids }),
      });

      if (!response.ok) throw new Error("Delete failed");

      // Remove deleted items from list
      setItems((prev) => prev.filter((item) => !ids.includes(item.id)));
    } catch (error) {
      console.error("Failed to delete items:", error);
      throw error;
    }
  };

  const emptyTrash = async () => {
    try {
      const response = await fetch("/api/trash/empty", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ tenantId }),
      });

      if (!response.ok) throw new Error("Empty trash failed");

      setItems([]);
    } catch (error) {
      console.error("Failed to empty trash:", error);
      throw error;
    }
  };

  return {
    items,
    isLoading,
    fetchTrashItems,
    restoreItems,
    deleteItems,
    emptyTrash,
  };
}
