/**
 * API call node form component.
 *
 * @doc.type component
 * @doc.purpose API call node configuration form
 * @doc.layer frontend
 * @doc.pattern React Component
 */

import React from "react";
import type { ApiCallNodeData } from "../../types/workflow.types";

/**
 * ApiCallForm component props.
 *
 * @doc.type interface
 */
interface ApiCallFormProps {
  data: ApiCallNodeData;
  onChange: (updates: Record<string, unknown>) => void;
  readOnly?: boolean;
}

/**
 * ApiCallForm component.
 *
 * @param props component props
 * @returns JSX element
 */
export const ApiCallForm: React.FC<ApiCallFormProps> = ({
  data,
  onChange,
  readOnly,
}) => {
  return (
    <div className="space-y-4">
      <div>
        <label
          htmlFor="apicallform-label-1"
          className="block text-sm font-medium text-gray-700 mb-2"
        >
          Label
        </label>
        <input
          id="apicallform-label-1"
          type="text"
          value={data.label || ""}
          onChange={(e) => onChange({ label: e.target.value })}
          disabled={readOnly}
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm disabled:bg-gray-50"
        />
      </div>

      <div>
        <label
          htmlFor="apicallform-method-2"
          className="block text-sm font-medium text-gray-700 mb-2"
        >
          Method
        </label>
        <select
          id="apicallform-method-2"
          value={data.method || "GET"}
          onChange={(e) => onChange({ method: e.target.value })}
          disabled={readOnly}
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm disabled:bg-gray-50"
        >
          <option>GET</option>
          <option>POST</option>
          <option>PUT</option>
          <option>DELETE</option>
          <option>PATCH</option>
        </select>
      </div>

      <div>
        <label
          htmlFor="apicallform-url-3"
          className="block text-sm font-medium text-gray-700 mb-2"
        >
          URL
        </label>
        <input
          id="apicallform-url-3"
          type="text"
          value={data.url || ""}
          onChange={(e) => onChange({ url: e.target.value })}
          disabled={readOnly}
          placeholder="https://api.example.com/endpoint"
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm disabled:bg-gray-50"
        />
      </div>

      <div>
        <label
          htmlFor="apicallform-authentication-4"
          className="block text-sm font-medium text-gray-700 mb-2"
        >
          Authentication
        </label>
        <select
          id="apicallform-authentication-4"
          value={data.authentication?.type || "none"}
          onChange={(e) =>
            onChange({
              authentication: {
                ...data.authentication,
                type: e.target.value as any,
              },
            })
          }
          disabled={readOnly}
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm disabled:bg-gray-50"
        >
          <option value="none">None</option>
          <option value="basic">Basic</option>
          <option value="bearer">Bearer Token</option>
          <option value="oauth2">OAuth2</option>
        </select>
      </div>

      {data.authentication?.type !== "none" && (
        <div>
          <label
            htmlFor="apicallform-credentials-5"
            className="block text-sm font-medium text-gray-700 mb-2"
          >
            Credentials
          </label>
          <input
            id="apicallform-credentials-5"
            type="password"
            value={data.authentication?.credentials || ""}
            onChange={(e) =>
              onChange({
                authentication: {
                  ...data.authentication,
                  credentials: e.target.value,
                },
              })
            }
            disabled={readOnly}
            className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm disabled:bg-gray-50"
          />
        </div>
      )}

      <div>
        <label
          htmlFor="apicallform-description-6"
          className="block text-sm font-medium text-gray-700 mb-2"
        >
          Description
        </label>
        <textarea
          id="apicallform-description-6"
          value={data.description || ""}
          onChange={(e) => onChange({ description: e.target.value })}
          disabled={readOnly}
          className="w-full px-3 py-2 border border-gray-300 rounded-md text-sm disabled:bg-gray-50"
          rows={3}
        />
      </div>
    </div>
  );
};
