import React from 'react';
import { useDropzone } from 'react-dropzone';
import { Upload, File, X } from 'lucide-react';
import './FileUpload.css';

const FileUpload = ({ 
  onFilesChange, 
  accept = { 'application/pdf': ['.pdf'] },
  multiple = false,
  files = [],
  maxFiles = multiple ? 10 : 1
}) => {
  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    accept,
    multiple,
    maxFiles,
    onDrop: (acceptedFiles) => {
      onFilesChange(multiple ? [...files, ...acceptedFiles] : acceptedFiles);
    },
  });

  const removeFile = (index) => {
    const newFiles = files.filter((_, i) => i !== index);
    onFilesChange(newFiles);
  };

  return (
    <div className="file-upload">
      <div 
        {...getRootProps()} 
        className={`dropzone ${isDragActive ? 'dropzone-active' : ''}`}
      >
        <input {...getInputProps()} />
        <div className="dropzone-content">
          <div className="dropzone-icon">
            <Upload size={48} />
          </div>
          {isDragActive ? (
            <p className="dropzone-text">Drop the files here...</p>
          ) : (
            <>
              <p className="dropzone-text">
                Drag & drop {multiple ? 'files' : 'a file'} here
              </p>
              <p className="dropzone-subtext">or click to browse</p>
            </>
          )}
          <p className="dropzone-hint">
            {multiple 
              ? `Supports up to ${maxFiles} PDF files` 
              : 'Supports PDF files'}
          </p>
        </div>
      </div>

      {files.length > 0 && (
        <div className="file-list">
          <h4 className="file-list-title">Uploaded Files</h4>
          <div className="file-list-items">
            {files.map((file, index) => (
              <div key={index} className="file-item">
                <div className="file-item-icon">
                  <File size={20} />
                </div>
                <div className="file-item-info">
                  <p className="file-item-name">{file.name}</p>
                  <p className="file-item-size">
                    {(file.size / 1024 / 1024).toFixed(2)} MB
                  </p>
                </div>
                <button
                  type="button"
                  className="file-item-remove"
                  onClick={() => removeFile(index)}
                  aria-label="Remove file"
                >
                  <X size={18} />
                </button>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};

export default FileUpload;
