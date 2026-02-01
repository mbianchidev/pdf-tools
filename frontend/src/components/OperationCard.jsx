import React from 'react';
// eslint-disable-next-line no-unused-vars
import { motion } from 'framer-motion';
import './OperationCard.css';

const OperationCard = ({ 
  icon, 
  title, 
  description, 
  isActive,
  onClick,
  children
}) => {
  const handleContentClick = (e) => {
    // Stop propagation to prevent card toggle when interacting with content
    e.stopPropagation();
  };

  return (
    <motion.div
      className={`operation-card ${isActive ? 'operation-card-active' : ''}`}
      onClick={onClick}
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.3 }}
      whileHover={!isActive ? { y: -4 } : {}}
    >
      <div className="operation-card-header">
        <div className="operation-card-icon">{icon}</div>
        <div className="operation-card-info">
          <h3 className="operation-card-title">{title}</h3>
          <p className="operation-card-description">{description}</p>
        </div>
      </div>
      
      {isActive && (
        <motion.div
          className="operation-card-content"
          initial={{ opacity: 0, height: 0 }}
          animate={{ opacity: 1, height: 'auto' }}
          exit={{ opacity: 0, height: 0 }}
          transition={{ duration: 0.3 }}
          onClick={handleContentClick}
        >
          {children}
        </motion.div>
      )}
    </motion.div>
  );
};

export default OperationCard;
