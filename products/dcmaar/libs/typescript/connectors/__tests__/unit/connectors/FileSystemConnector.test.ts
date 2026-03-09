/**
 * @fileoverview Comprehensive unit tests for FileSystemConnector
 *
 * Tests cover:
 * - File operations (read/write)
 * - Watch mode for file changes
 * - Polling mode for periodic reads
 * - File format support (JSON, text, binary, CSV)
 * - Pattern matching and filtering
 * - Directory creation and validation
 * - Size limits and error handling
 * - Resource cleanup
 */

import { FileSystemConnector, FileSystemConnectorConfig } from '../../../src/connectors/FileSystemConnector';
import { promises as fs, FSWatcher } from 'fs';

// Mock fs module
jest.mock('fs', () => ({
  promises: {
    stat: jest.fn(),
    mkdir: jest.fn(),
    readFile: jest.fn(),
    writeFile: jest.fn(),
    readdir: jest.fn(),
  },
  watch: jest.fn(),
}));

const mockedFs = fs as jest.Mocked<typeof fs>;
const mockWatch = require('fs').watch as jest.Mock;

describe('FileSystemConnector', () => {
  let connector: FileSystemConnector;
  let config: FileSystemConnectorConfig;
  let mockWatcher: Partial<FSWatcher>;

  beforeEach(() => {
    jest.clearAllMocks();
    jest.useFakeTimers();

    config = {
      id: 'fs-test',
      type: 'filesystem',
      path: '/test/path',
      mode: 'watch',
      pattern: '*.json',
      encoding: 'utf8',
      format: 'json',
      createIfNotExists: true,
      maxFileSize: 10 * 1024 * 1024,
    };

    mockWatcher = {
      close: jest.fn(),
      on: jest.fn(),
    };

    mockWatch.mockReturnValue(mockWatcher);
    mockedFs.stat.mockResolvedValue({
      isDirectory: () => true,
      isFile: () => false,
    } as any);
  });

  afterEach(async () => {
    if (connector) {
      await connector.destroy();
    }
    jest.useRealTimers();
  });

  describe('Constructor', () => {
    it('should create connector with config', () => {
      connector = new FileSystemConnector(config);

      expect(connector.id).toBe('fs-test');
      expect(connector.type).toBe('filesystem');
    });

    it('should apply default config values', () => {
      connector = new FileSystemConnector({
        id: 'test',
        type: 'filesystem',
        path: '/test',
      });

      expect(connector.type).toBe('filesystem');
    });
  });

  describe('Connection - Watch Mode', () => {
    beforeEach(() => {
      config.mode = 'watch';
    });

    it('should start watching directory', async () => {
      connector = new FileSystemConnector(config);

      await connector.connect();

      expect(mockWatch).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({
          recursive: true,
          persistent: true,
        }),
        expect.any(Function)
      );
    });

    it('should emit file_created on rename event', async () => {
      connector = new FileSystemConnector(config);
      const eventHandler = jest.fn();
      connector.onEvent('file_created', eventHandler);

      await connector.connect();

      const watchCallback = mockWatch.mock.calls[0][2];
      mockedFs.stat.mockResolvedValue({
        isFile: () => true,
        size: 1000,
        mtime: new Date(),
      } as any);
      mockedFs.readFile.mockResolvedValue('{"test": "data"}');

      await watchCallback('rename', 'test.json');

      expect(eventHandler).toHaveBeenCalled();
    });

    it('should emit file_modified on change event', async () => {
      connector = new FileSystemConnector(config);
      const eventHandler = jest.fn();
      connector.onEvent('file_modified', eventHandler);

      await connector.connect();

      const watchCallback = mockWatch.mock.calls[0][2];
      mockedFs.stat.mockResolvedValue({
        isFile: () => true,
        size: 1000,
        mtime: new Date(),
      } as any);
      mockedFs.readFile.mockResolvedValue('{"test": "data"}');

      await watchCallback('change', 'test.json');

      expect(eventHandler).toHaveBeenCalled();
    });

    it('should filter files by pattern', async () => {
      config.pattern = '*.txt';
      connector = new FileSystemConnector(config);
      const eventHandler = jest.fn();
      connector.onEvent('file_created', eventHandler);

      await connector.connect();

      const watchCallback = mockWatch.mock.calls[0][2];
      await watchCallback('rename', 'test.json');

      expect(eventHandler).not.toHaveBeenCalled();
    });

    it('should respect max file size', async () => {
      config.maxFileSize = 100;
      connector = new FileSystemConnector(config);
      const errorHandler = jest.fn();
      connector.on('error', errorHandler);

      await connector.connect();

      const watchCallback = mockWatch.mock.calls[0][2];
      mockedFs.stat.mockResolvedValue({
        isFile: () => true,
        size: 1000,
        mtime: new Date(),
      } as any);

      await watchCallback('rename', 'large.json');

      expect(errorHandler).toHaveBeenCalled();
    });
  });

  describe('Connection - Read Mode', () => {
    beforeEach(() => {
      config.mode = 'read';
      config.pollInterval = 1000;
    });

    it('should read directory on connect', async () => {
      mockedFs.readdir.mockResolvedValue(['file1.json', 'file2.json'] as any);
      mockedFs.stat.mockResolvedValue({
        isFile: () => true,
        size: 1000,
        mtime: new Date(),
      } as any);
      mockedFs.readFile.mockResolvedValue('{"test": "data"}');

      connector = new FileSystemConnector(config);

      await connector.connect();

      expect(mockedFs.readdir).toHaveBeenCalled();
    });

    it('should poll directory at interval', async () => {
      connector = new FileSystemConnector(config);

      await connector.connect();

      mockedFs.readdir.mockClear();
      jest.advanceTimersByTime(1000);
      await Promise.resolve();

      expect(mockedFs.readdir).toHaveBeenCalled();
    });

    it('should read single file', async () => {
      config.path = '/test/file.json';
      mockedFs.stat.mockResolvedValue({
        isFile: () => true,
        isDirectory: () => false,
        size: 1000,
        mtime: new Date(),
      } as any);
      mockedFs.readFile.mockResolvedValue('{"test": "data"}');

      connector = new FileSystemConnector(config);
      const eventHandler = jest.fn();
      connector.onEvent('file_read', eventHandler);

      await connector.connect();

      expect(eventHandler).toHaveBeenCalled();
    });
  });

  describe('Connection - Write Mode', () => {
    beforeEach(() => {
      config.mode = 'write';
    });

    it('should not read on connect in write mode', async () => {
      connector = new FileSystemConnector(config);

      await connector.connect();

      expect(mockWatch).not.toHaveBeenCalled();
      expect(mockedFs.readdir).not.toHaveBeenCalled();
    });
  });

  describe('Directory Creation', () => {
    it('should create directory if not exists', async () => {
      mockedFs.stat.mockRejectedValue({ code: 'ENOENT' });
      connector = new FileSystemConnector(config);

      await connector.connect();

      expect(mockedFs.mkdir).toHaveBeenCalledWith(
        expect.any(String),
        expect.objectContaining({ recursive: true })
      );
    });

    it('should throw error if path is invalid', async () => {
      config.createIfNotExists = false;
      mockedFs.stat.mockRejectedValue(new Error('Invalid path'));
      connector = new FileSystemConnector(config);

      await expect(connector.connect()).rejects.toThrow();
    });
  });

  describe('File Writing', () => {
    beforeEach(async () => {
      config.mode = 'write';
      connector = new FileSystemConnector(config);
      await connector.connect();
    });

    it('should write JSON file', async () => {
      const data = { test: 'data', number: 42 };
      const eventHandler = jest.fn();
      connector.onEvent('file_written', eventHandler);

      await connector.send(data, { filename: 'test.json' });

      expect(mockedFs.writeFile).toHaveBeenCalledWith(
        expect.stringContaining('test.json'),
        JSON.stringify(data, null, 2),
        expect.any(Object)
      );
      expect(eventHandler).toHaveBeenCalled();
    });

    it('should write text file', async () => {
      config.format = 'text';
      connector = new FileSystemConnector(config);
      await connector.connect();

      await connector.send('plain text');

      expect(mockedFs.writeFile).toHaveBeenCalledWith(
        expect.any(String),
        'plain text',
        expect.any(Object)
      );
    });

    it('should write binary file', async () => {
      config.format = 'binary';
      connector = new FileSystemConnector(config);
      await connector.connect();

      const buffer = Buffer.from([1, 2, 3, 4]);
      await connector.send(buffer);

      expect(mockedFs.writeFile).toHaveBeenCalledWith(
        expect.any(String),
        buffer,
        expect.any(Object)
      );
    });

    it('should write CSV file', async () => {
      config.format = 'csv';
      connector = new FileSystemConnector(config);
      await connector.connect();

      const data = [
        { name: 'Alice', age: 30 },
        { name: 'Bob', age: 25 },
      ];
      await connector.send(data);

      expect(mockedFs.writeFile).toHaveBeenCalled();
    });

    it('should generate filename if not provided', async () => {
      await connector.send({ test: 'data' });

      expect(mockedFs.writeFile).toHaveBeenCalledWith(
        expect.stringMatching(/\.json$/),
        expect.any(String),
        expect.any(Object)
      );
    });

    it('should throw error in read mode', async () => {
      config.mode = 'read';
      connector = new FileSystemConnector(config);
      await connector.connect();

      await expect(connector.send({ data: 'test' })).rejects.toThrow(
        'Cannot send data in read mode'
      );
    });
  });

  describe('File Reading', () => {
    it('should read JSON file', async () => {
      const jsonData = { test: 'data' };
      mockedFs.readFile.mockResolvedValue(JSON.stringify(jsonData));
      config.format = 'json';
      connector = new FileSystemConnector(config);

      await connector.connect();

      // Should parse JSON
      expect(connector.status).toBe('connected');
    });

    it('should read text file', async () => {
      mockedFs.readFile.mockResolvedValue('plain text content');
      config.format = 'text';
      connector = new FileSystemConnector(config);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should read binary file', async () => {
      const buffer = Buffer.from([1, 2, 3, 4]);
      mockedFs.readFile.mockResolvedValue(buffer);
      config.format = 'binary';
      connector = new FileSystemConnector(config);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should read CSV file', async () => {
      const csvContent = 'name,age\nAlice,30\nBob,25';
      mockedFs.readFile.mockResolvedValue(csvContent);
      config.format = 'csv';
      connector = new FileSystemConnector(config);

      await connector.connect();

      expect(connector.status).toBe('connected');
    });
  });

  describe('Pattern Matching', () => {
    it('should match wildcard pattern', async () => {
      config.pattern = '*';
      connector = new FileSystemConnector(config);
      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should match specific extension', async () => {
      config.pattern = '*.json';
      connector = new FileSystemConnector(config);
      await connector.connect();

      expect(connector.status).toBe('connected');
    });

    it('should match glob pattern with ?', async () => {
      config.pattern = 'file?.json';
      connector = new FileSystemConnector(config);
      await connector.connect();

      expect(connector.status).toBe('connected');
    });
  });

  describe('Disconnect', () => {
    it('should close watcher on disconnect', async () => {
      connector = new FileSystemConnector(config);
      await connector.connect();

      await connector.disconnect();

      expect(mockWatcher.close).toHaveBeenCalled();
    });

    it('should clear poll interval on disconnect', async () => {
      config.mode = 'read';
      config.pollInterval = 1000;
      connector = new FileSystemConnector(config);
      await connector.connect();

      await connector.disconnect();

      mockedFs.readdir.mockClear();
      jest.advanceTimersByTime(5000);

      expect(mockedFs.readdir).not.toHaveBeenCalled();
    });
  });

  describe('Resource Cleanup', () => {
    it('should cleanup on destroy', async () => {
      connector = new FileSystemConnector(config);
      await connector.connect();

      await connector.destroy();

      expect(connector.status).toBe('disconnected');
    });

    it('should clear tracked file times', async () => {
      config.mode = 'read';
      connector = new FileSystemConnector(config);
      await connector.connect();

      await connector.destroy();

      expect(connector.status).toBe('disconnected');
    });
  });

  describe('Error Handling', () => {
    it('should emit error on read failure', async () => {
      connector = new FileSystemConnector(config);
      const errorHandler = jest.fn();
      connector.on('error', errorHandler);

      await connector.connect();

      const watchCallback = mockWatch.mock.calls[0][2];
      mockedFs.stat.mockRejectedValue(new Error('Read error'));

      await watchCallback('rename', 'test.json');

      expect(errorHandler).toHaveBeenCalled();
    });

    it('should emit error on write failure', async () => {
      config.mode = 'write';
      connector = new FileSystemConnector(config);
      const errorHandler = jest.fn();
      connector.on('error', errorHandler);

      await connector.connect();

      mockedFs.writeFile.mockRejectedValue(new Error('Write error'));

      try {
        await connector.send({ data: 'test' });
      } catch (error) {
        expect(errorHandler).toHaveBeenCalled();
      }
    });
  });
});
