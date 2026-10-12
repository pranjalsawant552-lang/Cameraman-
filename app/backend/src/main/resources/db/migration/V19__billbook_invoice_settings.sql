-- Settings needed for the "bill-book style" invoice PDF (mirrors the physical
-- carbon-copy receipt book: studio name, proprietor name, tagline, no GST line).
INSERT INTO studio_settings (setting_key, setting_value, setting_type, category, description, is_public, is_editable)
VALUES
  ('studio.owner.name', '', 'STRING', 'BRANDING', 'Proprietor / contact person name shown on the bill-book invoice', TRUE, TRUE),
  ('studio.tagline', 'आमच्याकडे कमर्शियल, इंडस्ट्रियल फोटो, लग्न, साखरपुडा, वाढदिवस, कॉलेज फंक्शनची व सर्व प्रकारच्या समारंभाच्या फोटो व व्हिडिओ शुटिंगची कामे काळजीपूर्वक केली जातील.', 'STRING', 'BRANDING', 'Marathi service tagline shown on the bill-book invoice', TRUE, TRUE)
ON CONFLICT (setting_key) DO NOTHING;

