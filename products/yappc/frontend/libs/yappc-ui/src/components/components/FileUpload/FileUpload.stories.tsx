import React from 'react';

import { FileUpload, type UploadedFile } from './FileUpload';

import type { Meta, StoryObj } from '@storybook/react';

const meta: Meta<typeof FileUpload> = {
  title: 'Components/FileUpload',
  component: FileUpload,
  parameters: {
    layout: 'padded',
  },
  tags: ['autodocs'],
};

export default meta;
/**
 *
 */
type Story = StoryObj<typeof FileUpload>;

/**
 * Default file uploader
 */
export const Default: Story = {
  render: () => {
    const handleChange = (files: File[]) => {
      console.log('Files selected:', files);
    };

    return <FileUpload onChange={handleChange} />;
  },
};

/**
 * Multiple files
 */
export const MultipleFiles: Story = {
  render: () => {
    const handleChange = (files: File[]) => {
      console.log('Files selected:', files);
    };

    return <FileUpload multiple onChange={handleChange} />;
  },
};

/**
 * Images only
 */
export const ImagesOnly: Story = {
  render: () => {
    const handleChange = (files: File[]) => {
      console.log('Images selected:', files);
    };

    return <FileUpload accept="image/*" multiple onChange={handleChange} />;
  },
};

/**
 * With file size limit
 */
export const WithSizeLimit: Story = {
  render: () => {
    const maxSize = 5 * 1024 * 1024; // 5MB

    const handleChange = (files: File[]) => {
      console.log('Files selected:', files);
    };

    return <FileUpload maxSize={maxSize} multiple onChange={handleChange} />;
  },
};

/**
 * With max files limit
 */
export const MaxFilesLimit: Story = {
  render: () => {
    const handleChange = (files: File[]) => {
      console.log('Files selected:', files);
    };

    return <FileUpload multiple maxFiles={3} onChange={handleChange} />;
  },
};

/**
 * With upload simulation
 */
export const WithUpload: Story = {
  render: () => {
    const handleUpload = async (files: UploadedFile[]) => {
      // Simulate upload progress
      for (const file of files) {
        for (let progress = 0; progress <= 100; progress += 10) {
          await new Promise((resolve) => setTimeout(resolve, 100));
          file.progress = progress;
        }
      }
    };

    return <FileUpload multiple onUpload={handleUpload} />;
  },
};

/**
 * Without preview
 */
export const WithoutPreview: Story = {
  render: () => {
    return <FileUpload multiple showPreview={false} />;
  },
};

/**
 * Without file list
 */
export const WithoutFileList: Story = {
  render: () => {
    const handleChange = (files: File[]) => {
      console.log('Files selected:', files);
    };

    return <FileUpload multiple showFileList={false} onChange={handleChange} />;
  },
};

/**
 * Custom text
 */
export const CustomText: Story = {
  render: () => {
    return (
      <FileUpload
        multiple
        uploadText="Drop your files here"
        dragText="or click to browse"
      />
    );
  },
};

/**
 * Disabled state
 */
export const Disabled: Story = {
  render: () => {
    return <FileUpload disabled />;
  },
};

/**
 * Avatar upload example
 */
export const AvatarUpload: Story = {
  render: () => {
    const handleChange = (files: File[]) => {
      console.log('Avatar selected:', files[0]);
    };

    return (
      <div>
        <label className="block text-sm font-medium text-grey-700 mb-2">Profile Picture</label>
        <FileUpload
          accept="image/jpeg,image/png,image/webp"
          maxSize={2 * 1024 * 1024}
          onChange={handleChange}
          uploadText="Upload avatar"
          dragText="JPG, PNG or WebP (max 2MB)"
        />
      </div>
    );
  },
};

/**
 * Document upload example
 */
export const DocumentUpload: Story = {
  render: () => {
    const handleChange = (files: File[]) => {
      console.log('Documents selected:', files);
    };

    return (
      <div>
        <label className="block text-sm font-medium text-grey-700 mb-2">Upload Documents</label>
        <FileUpload
          accept=".pdf,.doc,.docx,.txt"
          multiple
          maxFiles={5}
          onChange={handleChange}
          uploadText="Upload documents"
          dragText="PDF, DOC, DOCX, or TXT (max 5 files)"
        />
      </div>
    );
  },
};

/**
 * Gallery upload example
 */
export const GalleryUpload: Story = {
  render: () => {
    const handleUpload = async (files: UploadedFile[]) => {
      for (const file of files) {
        for (let progress = 0; progress <= 100; progress += 20) {
          await new Promise((resolve) => setTimeout(resolve, 200));
          file.progress = progress;
        }
      }
    };

    return (
      <div>
        <label className="block text-sm font-medium text-grey-700 mb-2">Photo Gallery</label>
        <FileUpload
          accept="image/*"
          multiple
          maxSize={10 * 1024 * 1024}
          onUpload={handleUpload}
          uploadText="Add photos"
          dragText="Drop images here (max 10MB each)"
        />
      </div>
    );
  },
};

/**
 * Playground for experimenting with props
 */
export const Playground: Story = {
  args: {
    multiple: false,
    showPreview: true,
    showFileList: true,
    uploadText: 'Click to upload',
    dragText: 'or drag and drop',
    disabled: false,
  },
};
